/**
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ebay.myriad.api;

import com.codahale.metrics.annotation.Timed;
import com.ebay.myriad.api.model.FlexDownClusterRequest;
import com.ebay.myriad.api.model.FlexUpClusterRequest;
import com.ebay.myriad.scheduler.MyriadOperations;
import com.ebay.myriad.scheduler.NMProfileManager;
import com.ebay.myriad.state.SchedulerState;
import com.google.common.base.Preconditions;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * RESTful API to resource manager
 */
@Path("/cluster")
public class ClustersResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClustersResource.class);

    private final SchedulerState schedulerState;
    private final NMProfileManager profileManager;
    private final MyriadOperations myriadOperations;

    @Inject
    public ClustersResource(SchedulerState state,
                            NMProfileManager profileManager,
                            MyriadOperations myriadOperations) {
        this.schedulerState = state;
        this.profileManager = profileManager;
        this.myriadOperations = myriadOperations;
    }

    @Timed
    @PUT
    @Path("/flexup")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response flexUp(FlexUpClusterRequest request) {
        Preconditions.checkNotNull(request, "request object cannot be null or empty");

        Integer instances = request.getInstances();
        String profile = request.getProfile();
        LOGGER.info("Received flexup request. Profile: {}, Instances: {}", profile, instances);

        Response.ResponseBuilder response = Response.status(Response.Status.ACCEPTED);
        boolean isValidRequest = validateProfile(profile, response);
        isValidRequest = isValidRequest && validateInstances(instances, response);

        Response returnResponse = response.build();
        if (returnResponse.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
            this.myriadOperations.flexUpCluster(this.profileManager.get(profile), instances);
        }

        return returnResponse;
    }

    @Timed
    @PUT
    @Path("/flexdown")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response flexDown(FlexDownClusterRequest request) {
        Preconditions.checkNotNull(request, "request object cannot be null or empty");

        Integer instances = request.getInstances();
        String profile = request.getProfile();
        LOGGER.info("Received flex down request. Profile: {}, Instances: {}", profile, instances);

        Response.ResponseBuilder response = Response.status(Response.Status.ACCEPTED);
        boolean isValidRequest = validateProfile(profile, response);
        isValidRequest = isValidRequest && validateInstances(instances, response);

        Integer numFlexedUp = this.getNumFlexedupNMs();
        if (isValidRequest && numFlexedUp < instances)  {
            String message = String.format("Number of requested instances for flexdown is greater than the number of " +
                "Node Managers previously flexed up. Requested: %d, Previously flexed Up: %d. " +
                "Only %d Node Managers will be flexed down", instances, numFlexedUp, numFlexedUp);
            response.entity(message);
            LOGGER.warn(message);
        }

        Response returnResponse = response.build();
        if (returnResponse.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
            this.myriadOperations.flexDownCluster(profileManager.get(profile), instances);
        }
        return returnResponse;
    }

    private boolean validateProfile(String profile, ResponseBuilder response) {
      if (profile == null || profile.isEmpty()) {
        response.status(Response.Status.BAD_REQUEST).entity("'profile' is null or empty");
        LOGGER.error("'profile' is null or empty");
        return false;
      }
      if (!this.profileManager.exists(profile)) {
        response.status(Response.Status.BAD_REQUEST)
            .entity("Profile does not exist: '" + profile + "'");
        LOGGER.error("Provided profile does not exist: '" + profile + "'");
        return false;
      }
      return true;
    }

    private boolean validateInstances(Integer instances, ResponseBuilder response) {
      if (instances == null) {
        response.status(Response.Status.BAD_REQUEST).entity("'instances' is null");
        LOGGER.error("'instances' is null");
        return false;
      } else if (!(instances > 0)) {
          response.status(Response.Status.BAD_REQUEST)
                  .entity("Invalid instance size: " + instances);
          LOGGER.error("Invalid instance size request " + instances);
        return false;
      }
      return true;
    }

    private Integer getNumFlexedupNMs() {
        return this.schedulerState.getActiveTaskIds().size()
                + this.schedulerState.getStagingTaskIds().size()
                + this.schedulerState.getPendingTaskIds().size();
    }

}