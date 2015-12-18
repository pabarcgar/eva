/*
 * European Variation Archive (EVA) - Open-access database of all types of genetic
 * variation data from all species
 *
 * Copyright 2014, 2015 EMBL - European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.ebi.variation.eva.server.ws.ga4gh;

import io.swagger.annotations.Api;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang.StringUtils;
import org.opencb.biodata.ga4gh.GACallSet;
import org.opencb.biodata.ga4gh.GASearchCallSetsRequest;
import org.opencb.biodata.ga4gh.GASearchCallSetsResponse;
import org.opencb.biodata.models.variant.ga4gh.GACallSetFactory;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.lib.auth.IllegalOpenCGACredentialsException;
import org.opencb.opencga.storage.core.variant.adaptors.VariantSourceDBAdaptor;
import uk.ac.ebi.variation.eva.lib.datastore.DBAdaptorConnector;
import uk.ac.ebi.variation.eva.server.ws.EvaWSServer;

/**
 *
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 */
@Path("/v1/ga4gh/callsets")
@Produces(MediaType.APPLICATION_JSON)
@Api(tags = { "ga4gh", "samples" })
public class GA4GHVariantCallSetWSServer extends EvaWSServer {
    
    public GA4GHVariantCallSetWSServer(@Context UriInfo uriInfo, @Context HttpServletRequest hsr) {
        super(uriInfo, hsr);
    }

    
    @GET
    @Path("/search")
    /**
     * 
     * @see http://ga4gh.org/documentation/api/v0.5/ga4gh_api.html#/schema/org.ga4gh.GASearchCallSetsRequest
     */
    public Response getCallSets(@QueryParam("variantSetIds") String files,
                                @QueryParam("pageToken") String pageToken,
                                @DefaultValue("10") @QueryParam("pageSize") int limit,
                                @DefaultValue("false") @QueryParam("histogram") boolean histogram,
                                @DefaultValue("-1") @QueryParam("histogram_interval") int interval)
            throws UnknownHostException, IllegalOpenCGACredentialsException, IOException {
        
        if (files == null || files.isEmpty()) {
            return createJsonUserErrorResponse("The 'variantSetIds' argument must not be empty");
        }
        
        VariantSourceDBAdaptor dbAdaptor = DBAdaptorConnector.getVariantSourceDBAdaptor("hsapiens_grch37");
        
        int idxCurrentPage = 0;
        if (pageToken != null && !pageToken.isEmpty() && StringUtils.isNumeric(pageToken)) {
            idxCurrentPage = Integer.parseInt(pageToken);
            queryOptions.put("skip", idxCurrentPage * limit);
        }
        queryOptions.put("limit", limit);
        
        //List<String> filesList = Arrays.asList(files.split(","));
        List<String> filesList = getFilesFromParam(files);

        QueryResult<List<String>> qr;
        if (filesList.isEmpty()) {
            // TODO This should accept a global search for all call sets (samples) in the DB
            return createJsonUserErrorResponse("Please provide at least one variant set to search for");
        } else {
            qr = dbAdaptor.getSamplesBySources(filesList, queryOptions);
        }
        
        // Convert sample names objects to GACallSet
        filesList = translateFileIdsToNames(filesList);
        List<GACallSet> gaCallSets = GACallSetFactory.create(filesList, qr.getResult());
        // Calculate the next page token
        int idxLastElement = idxCurrentPage * limit + limit;
        String nextPageToken = (idxLastElement < qr.getNumTotalResults()) ? String.valueOf(idxCurrentPage + 1) : null;

        // Create the custom response for the GA4GH API
        return createJsonResponse(new GASearchCallSetsResponse(gaCallSets, nextPageToken));
    }

    private List<String> translateFileIdsToNames(List<String> filesList) {
        List<String> translatedFiles = new ArrayList<>();
        for (String file : filesList) {
            String translatedFile = erzIdsDict.get(file);
            if (translatedFile != null) {
                translatedFiles.add(translatedFile);
            } else {
                translatedFiles.add(file);
            }
        }
        return translatedFiles;
    }

    private List<String> getFilesFromParam(String filesParam) {
        List<String> filesList = new ArrayList<>();
        for (String file : filesParam.split(",")) {
            filesList.add(file);
            String translatedFile = erzNamesToNumericIdDict.get(file);
            if (translatedFile != null) {
                filesList.add(translatedFile);
            }
        }
        return filesList;
    }
    
    @POST
    @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getCallSets(GASearchCallSetsRequest request)
            throws UnknownHostException, IllegalOpenCGACredentialsException, IOException {
        return getCallSets(StringUtils.join(request.getVariantSetIds(), ","), request.getPageToken(), request.getPageSize(), false, -1);
    }
    
}
