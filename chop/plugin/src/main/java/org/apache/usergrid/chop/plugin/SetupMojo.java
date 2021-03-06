/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.usergrid.chop.plugin;


import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProjectHelper;

import org.apache.usergrid.chop.api.Project;
import org.apache.usergrid.chop.api.RestParams;
import org.apache.usergrid.chop.stack.SetupStackState;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;


@Mojo( name = "setup" )
public class SetupMojo extends MainMojo {

    @Component
    private MavenProjectHelper projectHelper;


    public SetupMojo() {

    }


    protected SetupMojo( MainMojo mojo ) {
        this.username = mojo.username;
        this.password = mojo.password;
        this.endpoint = mojo.endpoint;
        this.testPackageBase = mojo.testPackageBase;
        this.certStorePassphrase = mojo.certStorePassphrase;
        this.failIfCommitNecessary = mojo.failIfCommitNecessary;
        this.localRepository = mojo.localRepository;
        this.plugin = mojo.plugin;
        this.project = mojo.project;
        this.runnerCount = mojo.runnerCount;
        this.finalName = mojo.finalName;
    }


    @Override
    public void execute() throws MojoExecutionException {
        initCertStore();

        // Deploy if the local jar file is not uploaded or different from the one on the coordinator
        DeployMojo deployMojo = new DeployMojo( this );
        deployMojo.execute();

        Properties props = new Properties();
        try {
            File extractedConfigPropFile = new File( getExtractedRunnerPath(), PROJECT_FILE );
            FileInputStream inputStream = new FileInputStream( extractedConfigPropFile );
            props.load( inputStream );
            inputStream.close();
        }
        catch ( Exception e ) {
            LOG.error( "Error while reading project.properties in runner.jar", e );
            throw new MojoExecutionException( e.getMessage() );
        }

        /** Setup stack TODO use chop-client module to talk to the coordinator */
        DefaultClientConfig clientConfig = new DefaultClientConfig();
        Client client = Client.create( clientConfig );
        WebResource resource = client.resource( endpoint ).path( "/setup" );

        LOG.info( "Commit ID: {}", props.getProperty( Project.GIT_UUID_KEY ) );
        LOG.info( "Artifact Id: {}", props.getProperty( Project.ARTIFACT_ID_KEY ) );
        LOG.info( "Group Id: {}", props.getProperty( Project.GROUP_ID_KEY ) );
        LOG.info( "Version: {}", props.getProperty( Project.PROJECT_VERSION_KEY ) );
        LOG.info( "Username: {}", username );

        ClientResponse resp = resource.path( "/stack" )
                                      .queryParam( RestParams.COMMIT_ID, props.getProperty( Project.GIT_UUID_KEY ) )
                                      .queryParam( RestParams.MODULE_ARTIFACTID,
                                              props.getProperty( Project.ARTIFACT_ID_KEY ) )
                                      .queryParam( RestParams.MODULE_GROUPID,
                                              props.getProperty( Project.GROUP_ID_KEY ) )
                                      .queryParam( RestParams.MODULE_VERSION,
                                              props.getProperty( Project.PROJECT_VERSION_KEY ) )
                                      .queryParam( RestParams.USERNAME, username )
                                      .queryParam( RestParams.RUNNER_COUNT, runnerCount.toString() )
                                      .type( MediaType.APPLICATION_JSON )
                                      .accept( MediaType.APPLICATION_JSON )
                                      .post( ClientResponse.class );

        if( resp.getStatus() != Response.Status.OK.getStatusCode() &&
                resp.getStatus() != Response.Status.CREATED.getStatusCode() ) {
            LOG.error( "Could not get the status from coordinator, HTTP status: {}", resp.getStatus() );
            LOG.error( "Error Message: {}", resp.getEntity( String.class ) );

            throw new MojoExecutionException( "Setup plugin goal has failed" );
        }

        String responseMessage = resp.getEntity( String.class );

        LOG.info( "====== Response from the coordinator ======" );
        LOG.info( responseMessage );
        LOG.info( "===========================================" );




    }
}
