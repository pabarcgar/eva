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

package uk.ac.ebi.variation.eva.server.ws;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.Splitter;
import io.swagger.annotations.ApiParam;

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.biodata.models.variant.VariantSourceEntry;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResponse;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.variant.io.json.GenotypeJsonMixin;
import org.opencb.opencga.storage.core.variant.io.json.VariantSourceEntryJsonMixin;
import org.opencb.opencga.storage.core.variant.io.json.VariantSourceJsonMixin;
import org.opencb.opencga.storage.core.variant.io.json.VariantStatsJsonMixin;
import org.opencb.opencga.storage.core.variant.io.json.VariantStatsJsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.variation.eva.server.exception.SpeciesException;
import uk.ac.ebi.variation.eva.server.exception.VersionException;

/**
 * Created by imedina on 01/04/14.
 */
@Produces(MediaType.APPLICATION_JSON)
public class EvaWSServer {

    protected final String version = "v1";

    @DefaultValue("Homo sapiens")
    @QueryParam("species")
    @ApiParam(name = "species", value = "Excluded fields will not be returned. Comma separated JSON paths must be provided",
            defaultValue = "hsapiens", allowableValues = "hsapiens,mmusculus")
    protected String species;

    protected UriInfo uriInfo;
    protected HttpServletRequest httpServletRequest;

    protected QueryOptions queryOptions;
    protected QueryResponse queryResponse;
    protected long startTime;
    protected long endTime;

    @DefaultValue("")
    @QueryParam("exclude")
    @ApiParam(name = "excluded fields", value = "Excluded fields will not be returned. Comma separated JSON paths must be provided")
    protected String exclude;

    @DefaultValue("")
    @QueryParam("include")
    @ApiParam(name = "included fields", value = "Included fields are the only to be returned. Comma separated JSON path must be provided")
    protected String include;

    @DefaultValue("-1")
    @QueryParam("limit")
    @ApiParam(name = "limit", value = "Max number of results to be returned. No limit applied when -1 [-1]")
    protected int limit;

    @DefaultValue("-1")
    @QueryParam("skip")
    @ApiParam(name = "skip", value = "Number of results to be skipped. No skip applied when -1 [-1]")
    protected int skip;

    @DefaultValue("false")
    @QueryParam("count")
    @ApiParam(name = "count", value = "The total number of results is returned [false]")
    protected String count;

    protected static ObjectMapper jsonObjectMapper;
    protected static ObjectWriter jsonObjectWriter;
    protected static Map<String, String> dict;
    private static Map<String, String> studyDict;
    protected static Map<String, String> erzIdsDict;
    protected static Map<String, String> erzNamesToNumericIdDict;


    protected static Logger logger;

    private boolean translateStudyIds;
    private boolean translateFileIds;

    static {
        logger = LoggerFactory.getLogger(EvaWSServer.class);
        logger.info("EvaWSServer: Initialising attributes inside static block");

        jsonObjectMapper = new ObjectMapper();
        jsonObjectMapper.addMixInAnnotations(VariantSourceEntry.class, VariantSourceEntryJsonMixin.class);
        jsonObjectMapper.addMixInAnnotations(Genotype.class, GenotypeJsonMixin.class);
        jsonObjectMapper.addMixInAnnotations(VariantStats.class, VariantStatsJsonMixin.class);
        jsonObjectMapper.addMixInAnnotations(VariantSource.class, VariantSourceJsonMixin.class);
        jsonObjectMapper.setSerializationInclusion(Include.NON_NULL);

        SimpleModule module = new SimpleModule();
        module.addSerializer(VariantStats.class, new VariantStatsJsonSerializer());
        jsonObjectMapper.registerModule(module);

        jsonObjectWriter = jsonObjectMapper.writer();

        dict = new HashMap<>();
        dict.put("PRJEB4019", "8616");
        dict.put("PRJEB5439","2");
        dict.put("PRJEB8661","130");
        dict.put("PRJEB5829","156");
        dict.put("PRJEB6041","5385");
        dict.put("PRJEB6042","5404");
        dict.put("PRJEB7895","5423");
        dict.put("PRJEB6930","301");
        dict.put("PRJEB7218","5442");
        dict.put("PRJEB8705","5459");
        dict.put("PRJEB7217","5480");
        dict.put("PRJEB8652","5509");
        dict.put("PRJEB8650","5643");
        dict.put("PRJEB7923","5778");
        dict.put("PRJEB7894","6558");
        dict.put("PRJEB6911","8413");
        dict.put("PRJEB5473","8476");
        dict.put("PRJEB4395","8531");
        dict.put("PRJEB8639","11645");
        dict.put("PRJEB6025","12002");
        dict.put("PRJEB6495","12204");
        dict.put("PRJEB5978","12235");
        dict.put("PRJEB6057","12270");
        dict.put("PRJEB6119","12474");
        dict.put("PRJEB7061","12504");
        dict.put("PRJEB7723","12569");
        dict.put("PRJEB9507","33687");
        dict.put("PRJEB629","34064");

        studyDict = new HashMap<>();
        studyDict.put("5509", "PRJEB8652");
        studyDict.put("5459", "PRJEB8705");
        studyDict.put("11645", "PRJEB8639");
        studyDict.put("5480", "PRJEB7217");
        studyDict.put("2", "PRJEB5439");
        studyDict.put("5423", "PRJEB7895");
        studyDict.put("156", "PRJEB5829");
        studyDict.put("301", "PRJEB6930");
        studyDict.put("5442", "PRJEB7218");
        studyDict.put("8616", "PRJEB4019");
        studyDict.put("34711", "PRJX00001");
        studyDict.put("130", "PRJEB8661");
        studyDict.put("5385", "PRJEB6041");
        studyDict.put("5643", "PRJEB8650");
        studyDict.put("5404", "PRJEB6042");

        erzIdsDict = new HashMap<>();
        erzIdsDict.put("52", "ERZ017134");
        erzIdsDict.put("8603", "ERZX00038");
        erzIdsDict.put("34886", "ERZX00061");
        erzIdsDict.put("208", "ERZ019953");
        erzIdsDict.put("11768", "ERZ094141");
        erzIdsDict.put("11795", "ERZ094140");
        erzIdsDict.put("5552", "ERZ094203");
        erzIdsDict.put("11727", "ERZ094147");
        erzIdsDict.put("11724", "ERZ094137");
        erzIdsDict.put("5558", "ERZ094199");
        erzIdsDict.put("11523", "ERZ015357");
        erzIdsDict.put("5554", "ERZ094211");
        erzIdsDict.put("5560", "ERZ094202");
        erzIdsDict.put("5577", "ERZ094190");
        erzIdsDict.put("11507", "ERZ015346");
        erzIdsDict.put("5683", "ERZ094176");
        erzIdsDict.put("11535", "ERZ015350");
        erzIdsDict.put("34888", "ERZX00059");
        erzIdsDict.put("5700", "ERZ094177");
        erzIdsDict.put("5545", "ERZ094208");
        erzIdsDict.put("5572", "ERZ094193");
        erzIdsDict.put("5691", "ERZ094167");
        erzIdsDict.put("5676", "ERZ094168");
        erzIdsDict.put("34858", "ERZX00067");
        erzIdsDict.put("218", "ERZ019961");
        erzIdsDict.put("5708", "ERZ094179");
        erzIdsDict.put("5399", "ERZ038109");
        erzIdsDict.put("8591", "ERZX00040");
        erzIdsDict.put("5688", "ERZ094181");
        erzIdsDict.put("11517", "ERZ015355");
        erzIdsDict.put("56", "ERZ017133");
        erzIdsDict.put("34866", "ERZX00060");
        erzIdsDict.put("5722", "ERZ094189");
        erzIdsDict.put("72", "ERZ017138");
        erzIdsDict.put("8585", "ERZX00051");
        erzIdsDict.put("230", "ERZ019952");
        erzIdsDict.put("11745", "ERZ094131");
        erzIdsDict.put("11713", "ERZ094144");
        erzIdsDict.put("8611", "ERZX00042");
        erzIdsDict.put("5437", "ERZ049522");
        erzIdsDict.put("11705", "ERZ094135");
        erzIdsDict.put("11756", "ERZ094138");
        erzIdsDict.put("5695", "ERZ094180");
        erzIdsDict.put("175", "ERZ019949");
        erzIdsDict.put("34856", "ERZX00069");
        erzIdsDict.put("216", "ERZ019947");
        erzIdsDict.put("11758", "ERZ094146");
        erzIdsDict.put("5673", "ERZ094188");
        erzIdsDict.put("11742", "ERZ094136");
        erzIdsDict.put("5706", "ERZ094175");
        erzIdsDict.put("34923", "ERZX00058");
        erzIdsDict.put("5549", "ERZ094192");
        erzIdsDict.put("5418", "ERZX00026");
        erzIdsDict.put("11466", "ERZ015359");
        erzIdsDict.put("5716", "ERZ094171");
        erzIdsDict.put("11469", "ERZ015345");
        erzIdsDict.put("83", "ERZ017131");
        erzIdsDict.put("203", "ERZ019944");
        erzIdsDict.put("228", "ERZ019942");
        erzIdsDict.put("70", "ERZ017132");
        erzIdsDict.put("11485", "ERZ015362");
        erzIdsDict.put("6550", "ERZ017128");
        erzIdsDict.put("11810", "ERZ094152");
        erzIdsDict.put("122", "ERZ017144");
        erzIdsDict.put("11463", "ERZ015363");
        erzIdsDict.put("5553", "ERZ094198");
        erzIdsDict.put("24", "ERZ017137");
        erzIdsDict.put("5581", "ERZ094194");
        erzIdsDict.put("5380", "ERZX00049");
        erzIdsDict.put("5590", "ERZ094201");
        erzIdsDict.put("5477", "ERZ038104");
        erzIdsDict.put("11723", "ERZ094145");
        erzIdsDict.put("5692", "ERZ094169");
        erzIdsDict.put("8593", "ERZX00046");
        erzIdsDict.put("189", "ERZ019955");
        erzIdsDict.put("5701", "ERZ094186");
        erzIdsDict.put("5473", "ERZ097166");
        erzIdsDict.put("11525", "ERZ015365");
        erzIdsDict.put("185", "ERZX00006");
        erzIdsDict.put("8597", "ERZX00039");
        erzIdsDict.put("11489", "ERZ015352");
        erzIdsDict.put("292", "ERZ019948");
        erzIdsDict.put("5684", "ERZ094173");
        erzIdsDict.put("5562", "ERZ094210");
        erzIdsDict.put("8614", "ERZX00052");
        erzIdsDict.put("11731", "ERZ094139");
        erzIdsDict.put("35", "ERZ017123");
        erzIdsDict.put("11491", "ERZ015353");
        erzIdsDict.put("11477", "ERZ015358");
        erzIdsDict.put("5709", "ERZ094185");
        erzIdsDict.put("11529", "ERZ015356");
        erzIdsDict.put("34870", "ERZX00070");
        erzIdsDict.put("11701", "ERZ094151");
        erzIdsDict.put("11452", "ERZX00033");
        erzIdsDict.put("76", "ERZ017136");
        erzIdsDict.put("8599", "ERZX00034");
        erzIdsDict.put("34879", "ERZX00066");
        erzIdsDict.put("34896", "ERZX00068");
        erzIdsDict.put("8589", "ERZX00044");
        erzIdsDict.put("8587", "ERZX00031");
        erzIdsDict.put("294", "ERZ019962");
        erzIdsDict.put("5680", "ERZ094174");
        erzIdsDict.put("274", "ERZ019957");
        erzIdsDict.put("8583", "ERZX00043");
        erzIdsDict.put("110", "ERZ017140");
        erzIdsDict.put("5598", "ERZ094191");
        erzIdsDict.put("58", "ERZ017141");
        erzIdsDict.put("11703", "ERZ094149");
        erzIdsDict.put("34872", "ERZX00071");
        erzIdsDict.put("34864", "ERZX00063");
        erzIdsDict.put("5494", "ERZ038105");
        erzIdsDict.put("34878", "ERZX00054");
        erzIdsDict.put("5725", "ERZ094182");
        erzIdsDict.put("11736", "ERZ094133");
        erzIdsDict.put("5721", "ERZ094178");
        erzIdsDict.put("11729", "ERZ094134");
        erzIdsDict.put("11460", "ERZ015710");
        erzIdsDict.put("34862", "ERZX00055");
        erzIdsDict.put("60", "ERZ017126");
        erzIdsDict.put("5743", "ERZ094172");
        erzIdsDict.put("8605", "ERZX00045");
        erzIdsDict.put("34876", "ERZX00076");
        erzIdsDict.put("5719", "ERZ094166");
        erzIdsDict.put("46", "ERZ017125");
        erzIdsDict.put("5544", "ERZ094197");
        erzIdsDict.put("34852", "ERZX00064");
        erzIdsDict.put("54", "ERZ017142");
        erzIdsDict.put("5579", "ERZ094196");
        erzIdsDict.put("5382", "ERZX00035");
        erzIdsDict.put("8601", "ERZX00037");
        erzIdsDict.put("48", "ERZ017121");
        erzIdsDict.put("34884", "ERZX00074");
        erzIdsDict.put("11707", "ERZ094148");
        erzIdsDict.put("11482","ERZ015354");
        erzIdsDict.put("11712", "ERZ094132");
        erzIdsDict.put("11501", "ERZ015349");
        erzIdsDict.put("5584", "ERZ094213");
        erzIdsDict.put("11511", "ERZ015368");
        erzIdsDict.put("81", "ERZ017127");
        erzIdsDict.put("187", "ERZ019943");
        erzIdsDict.put("34894", "ERZX00075");
        erzIdsDict.put("8609", "ERZX00036");
        erzIdsDict.put("11753", "ERZ094129");
        erzIdsDict.put("11760", "ERZ094143");
        erzIdsDict.put("282", "ERZ019959");
        erzIdsDict.put("11721", "ERZ094142");
        erzIdsDict.put("5702", "ERZ094183");
        erzIdsDict.put("5568", "ERZ094209");
        erzIdsDict.put("5697", "ERZ094187");
        erzIdsDict.put("5687", "ERZ094170");
        erzIdsDict.put("41", "ERZ017143");
        erzIdsDict.put("5588", "ERZ094212");
        erzIdsDict.put("11455", "ERZX00048");
        erzIdsDict.put("224", "ERZ019960");
        erzIdsDict.put("5586", "ERZ094200");
        erzIdsDict.put("30", "ERZ017135");
        erzIdsDict.put("8607", "ERZX00032");
        erzIdsDict.put("34882", "ERZX00072");
        erzIdsDict.put("11734", "ERZ094130");
        erzIdsDict.put("11519", "ERZ015347");
        erzIdsDict.put("11717", "ERZ094150");
        erzIdsDict.put("9861", "ERZX00041");
        erzIdsDict.put("222", "ERZ019946");
        erzIdsDict.put("5566", "ERZ094206");
        erzIdsDict.put("210", "ERZ019956");
        erzIdsDict.put("226", "ERZ019950");
        erzIdsDict.put("34854", "ERZX00057");
        erzIdsDict.put("37", "ERZ017129");
        erzIdsDict.put("85", "ERZ017124");
        erzIdsDict.put("6521", "ERZ017139");
        erzIdsDict.put("34860", "ERZX00056");
        erzIdsDict.put("278", "ERZ019954");
        erzIdsDict.put("5712", "ERZ094184");
        erzIdsDict.put("193", "ERZ019940");
        erzIdsDict.put("11521", "ERZ015351");
        erzIdsDict.put("34850", "ERZX00073");
        erzIdsDict.put("27", "ERZ017130");
        erzIdsDict.put("5564", "ERZ094207");
        erzIdsDict.put("11474", "ERZ015369");
        erzIdsDict.put("50", "ERZ017122");
        erzIdsDict.put("232", "ERZ019958");
        erzIdsDict.put("34705", "ERZ108740");
        erzIdsDict.put("34890", "ERZX00065");
        erzIdsDict.put("11471", "ERZ015361");
        erzIdsDict.put("8595", "ERZX00047");
        erzIdsDict.put("34892", "ERZX00053");
        erzIdsDict.put("5537", "ERZ094205");
        erzIdsDict.put("11495", "ERZ015348");
        erzIdsDict.put("11531", "ERZ015366");
        erzIdsDict.put("212", "ERZ019941");
        erzIdsDict.put("5542", "ERZ094204");
        erzIdsDict.put("5574", "ERZ094195");
        erzIdsDict.put("11527", "ERZ015367");
        erzIdsDict.put("34926", "ERZX00062");

        erzNamesToNumericIdDict = new HashMap<>();
        erzNamesToNumericIdDict.put("ERZ017134", "52");
        erzNamesToNumericIdDict.put("ERZX00038", "8603");
        erzNamesToNumericIdDict.put("ERZX00061", "34886");
        erzNamesToNumericIdDict.put("ERZ019953", "208");
        erzNamesToNumericIdDict.put("ERZ094141", "11768");
        erzNamesToNumericIdDict.put("ERZ094140", "11795");
        erzNamesToNumericIdDict.put("ERZ094203", "5552");
        erzNamesToNumericIdDict.put("ERZ094147", "11727");
        erzNamesToNumericIdDict.put("ERZ094137", "11724");
        erzNamesToNumericIdDict.put("ERZ094199", "5558");
        erzNamesToNumericIdDict.put("ERZ015357", "11523");
        erzNamesToNumericIdDict.put("ERZ094211", "5554");
        erzNamesToNumericIdDict.put("ERZ094202", "5560");
        erzNamesToNumericIdDict.put("ERZ094190", "5577");
        erzNamesToNumericIdDict.put("ERZ015346", "11507");
        erzNamesToNumericIdDict.put("ERZ094176", "5683");
        erzNamesToNumericIdDict.put("ERZ015350", "11535");
        erzNamesToNumericIdDict.put("ERZX00059", "34888");
        erzNamesToNumericIdDict.put("ERZ094177", "5700");
        erzNamesToNumericIdDict.put("ERZ094208", "5545");
        erzNamesToNumericIdDict.put("ERZ094193", "5572");
        erzNamesToNumericIdDict.put("ERZ094167", "5691");
        erzNamesToNumericIdDict.put("ERZ094168", "5676");
        erzNamesToNumericIdDict.put("ERZX00067", "34858");
        erzNamesToNumericIdDict.put("ERZ019961", "218");
        erzNamesToNumericIdDict.put("ERZ094179", "5708");
        erzNamesToNumericIdDict.put("ERZ038109", "5399");
        erzNamesToNumericIdDict.put("ERZX00040", "8591");
        erzNamesToNumericIdDict.put("ERZ094181", "5688");
        erzNamesToNumericIdDict.put("ERZ015355", "11517");
        erzNamesToNumericIdDict.put("ERZ017133", "56");
        erzNamesToNumericIdDict.put("ERZX00060", "34866");
        erzNamesToNumericIdDict.put("ERZ094189", "5722");
        erzNamesToNumericIdDict.put("ERZ017138", "72");
        erzNamesToNumericIdDict.put("ERZX00051", "8585");
        erzNamesToNumericIdDict.put("ERZ019952", "230");
        erzNamesToNumericIdDict.put("ERZ094131", "11745");
        erzNamesToNumericIdDict.put("ERZ094144", "11713");
        erzNamesToNumericIdDict.put("ERZX00042", "8611");
        erzNamesToNumericIdDict.put("ERZ049522", "5437");
        erzNamesToNumericIdDict.put("ERZ094135", "11705");
        erzNamesToNumericIdDict.put("ERZ094138", "11756");
        erzNamesToNumericIdDict.put("ERZ094180", "5695");
        erzNamesToNumericIdDict.put("ERZ019949", "175");
        erzNamesToNumericIdDict.put("ERZX00069", "34856");
        erzNamesToNumericIdDict.put("ERZ019947", "216");
        erzNamesToNumericIdDict.put("ERZ094146", "11758");
        erzNamesToNumericIdDict.put("ERZ094188", "5673");
        erzNamesToNumericIdDict.put("ERZ094136", "11742");
        erzNamesToNumericIdDict.put("ERZ094175", "5706");
        erzNamesToNumericIdDict.put("ERZX00058", "34923");
        erzNamesToNumericIdDict.put("ERZ094192", "5549");
        erzNamesToNumericIdDict.put("ERZX00026", "5418");
        erzNamesToNumericIdDict.put("ERZ015359", "11466");
        erzNamesToNumericIdDict.put("ERZ094171", "5716");
        erzNamesToNumericIdDict.put("ERZ015345", "11469");
        erzNamesToNumericIdDict.put("ERZ017131", "83");
        erzNamesToNumericIdDict.put("ERZ019944", "203");
        erzNamesToNumericIdDict.put("ERZ019942", "228");
        erzNamesToNumericIdDict.put("ERZ017132", "70");
        erzNamesToNumericIdDict.put("ERZ015362", "11485");
        erzNamesToNumericIdDict.put("ERZ017128", "6550");
        erzNamesToNumericIdDict.put("ERZ094152", "11810");
        erzNamesToNumericIdDict.put("ERZ017144", "122");
        erzNamesToNumericIdDict.put("ERZ015363", "11463");
        erzNamesToNumericIdDict.put("ERZ094198", "5553");
        erzNamesToNumericIdDict.put("ERZ017137", "24");
        erzNamesToNumericIdDict.put("ERZ094194", "5581");
        erzNamesToNumericIdDict.put("ERZX00049", "5380");
        erzNamesToNumericIdDict.put("ERZ094201", "5590");
        erzNamesToNumericIdDict.put("ERZ038104", "5477");
        erzNamesToNumericIdDict.put("ERZ094145", "11723");
        erzNamesToNumericIdDict.put("ERZ094169", "5692");
        erzNamesToNumericIdDict.put("ERZX00046", "8593");
        erzNamesToNumericIdDict.put("ERZ019955", "189");
        erzNamesToNumericIdDict.put("ERZ094186", "5701");
        erzNamesToNumericIdDict.put("ERZ097166", "5473");
        erzNamesToNumericIdDict.put("ERZ015365", "11525");
        erzNamesToNumericIdDict.put("ERZX00006", "185");
        erzNamesToNumericIdDict.put("ERZX00039", "8597");
        erzNamesToNumericIdDict.put("ERZ015352", "11489");
        erzNamesToNumericIdDict.put("ERZ019948", "292");
        erzNamesToNumericIdDict.put("ERZ094173", "5684");
        erzNamesToNumericIdDict.put("ERZ094210", "5562");
        erzNamesToNumericIdDict.put("ERZX00052", "8614");
        erzNamesToNumericIdDict.put("ERZ094139", "11731");
        erzNamesToNumericIdDict.put("ERZ017123", "35");
        erzNamesToNumericIdDict.put("ERZ015353", "11491");
        erzNamesToNumericIdDict.put("ERZ015358", "11477");
        erzNamesToNumericIdDict.put("ERZ094185", "5709");
        erzNamesToNumericIdDict.put("ERZ015356", "11529");
        erzNamesToNumericIdDict.put("ERZX00070", "34870");
        erzNamesToNumericIdDict.put("ERZ094151", "11701");
        erzNamesToNumericIdDict.put("ERZX00033", "11452");
        erzNamesToNumericIdDict.put("ERZ017136", "76");
        erzNamesToNumericIdDict.put("ERZX00034", "8599");
        erzNamesToNumericIdDict.put("ERZX00066", "34879");
        erzNamesToNumericIdDict.put("ERZX00068", "34896");
        erzNamesToNumericIdDict.put("ERZX00044", "8589");
        erzNamesToNumericIdDict.put("ERZX00031", "8587");
        erzNamesToNumericIdDict.put("ERZ019962", "294");
        erzNamesToNumericIdDict.put("ERZ094174", "5680");
        erzNamesToNumericIdDict.put("ERZ019957", "274");
        erzNamesToNumericIdDict.put("ERZX00043", "8583");
        erzNamesToNumericIdDict.put("ERZ017140", "110");
        erzNamesToNumericIdDict.put("ERZ094191", "5598");
        erzNamesToNumericIdDict.put("ERZ017141", "58");
        erzNamesToNumericIdDict.put("ERZ094149", "11703");
        erzNamesToNumericIdDict.put("ERZX00071", "34872");
        erzNamesToNumericIdDict.put("ERZX00063", "34864");
        erzNamesToNumericIdDict.put("ERZ038105", "5494");
        erzNamesToNumericIdDict.put("ERZX00054", "34878");
        erzNamesToNumericIdDict.put("ERZ094182", "5725");
        erzNamesToNumericIdDict.put("ERZ094133", "11736");
        erzNamesToNumericIdDict.put("ERZ094178", "5721");
        erzNamesToNumericIdDict.put("ERZ094134", "11729");
        erzNamesToNumericIdDict.put("ERZ015710", "11460");
        erzNamesToNumericIdDict.put("ERZX00055", "34862");
        erzNamesToNumericIdDict.put("ERZ017126", "60");
        erzNamesToNumericIdDict.put("ERZ094172", "5743");
        erzNamesToNumericIdDict.put("ERZX00045", "8605");
        erzNamesToNumericIdDict.put("ERZX00076", "34876");
        erzNamesToNumericIdDict.put("ERZ094166", "5719");
        erzNamesToNumericIdDict.put("ERZ017125", "46");
        erzNamesToNumericIdDict.put("ERZ094197", "5544");
        erzNamesToNumericIdDict.put("ERZX00064", "34852");
        erzNamesToNumericIdDict.put("ERZ017142", "54");
        erzNamesToNumericIdDict.put("ERZ094196", "5579");
        erzNamesToNumericIdDict.put("ERZX00035", "5382");
        erzNamesToNumericIdDict.put("ERZX00037", "8601");
        erzNamesToNumericIdDict.put("ERZ017121", "48");
        erzNamesToNumericIdDict.put("ERZX00074", "34884");
        erzNamesToNumericIdDict.put("ERZ094148", "11707");
        erzNamesToNumericIdDict.put("ERZ015354", "11482");
        erzNamesToNumericIdDict.put("ERZ094132", "11712");
        erzNamesToNumericIdDict.put("ERZ015349", "11501");
        erzNamesToNumericIdDict.put("ERZ094213", "5584");
        erzNamesToNumericIdDict.put("ERZ015368", "11511");
        erzNamesToNumericIdDict.put("ERZ017127", "81");
        erzNamesToNumericIdDict.put("ERZ019943", "187");
        erzNamesToNumericIdDict.put("ERZX00075", "34894");
        erzNamesToNumericIdDict.put("ERZX00036", "8609");
        erzNamesToNumericIdDict.put("ERZ094129", "11753");
        erzNamesToNumericIdDict.put("ERZ094143", "11760");
        erzNamesToNumericIdDict.put("ERZ019959", "282");
        erzNamesToNumericIdDict.put("ERZ094142", "11721");
        erzNamesToNumericIdDict.put("ERZ094183", "5702");
        erzNamesToNumericIdDict.put("ERZ094209", "5568");
        erzNamesToNumericIdDict.put("ERZ094187", "5697");
        erzNamesToNumericIdDict.put("ERZ094170", "5687");
        erzNamesToNumericIdDict.put("ERZ017143", "41");
        erzNamesToNumericIdDict.put("ERZ094212", "5588");
        erzNamesToNumericIdDict.put("ERZX00048", "11455");
        erzNamesToNumericIdDict.put("ERZ019960", "224");
        erzNamesToNumericIdDict.put("ERZ094200", "5586");
        erzNamesToNumericIdDict.put("ERZ017135", "30");
        erzNamesToNumericIdDict.put("ERZX00032", "8607");
        erzNamesToNumericIdDict.put("ERZX00072", "34882");
        erzNamesToNumericIdDict.put("ERZ094130", "11734");
        erzNamesToNumericIdDict.put("ERZ015347", "11519");
        erzNamesToNumericIdDict.put("ERZ094150", "11717");
        erzNamesToNumericIdDict.put("ERZX00041", "9861");
        erzNamesToNumericIdDict.put("ERZ019946", "222");
        erzNamesToNumericIdDict.put("ERZ094206", "5566");
        erzNamesToNumericIdDict.put("ERZ019956", "210");
        erzNamesToNumericIdDict.put("ERZ019950", "226");
        erzNamesToNumericIdDict.put("ERZX00057", "34854");
        erzNamesToNumericIdDict.put("ERZ017129", "37");
        erzNamesToNumericIdDict.put("ERZ017124", "85");
        erzNamesToNumericIdDict.put("ERZ017139", "6521");
        erzNamesToNumericIdDict.put("ERZX00056", "34860");
        erzNamesToNumericIdDict.put("ERZ019954", "278");
        erzNamesToNumericIdDict.put("ERZ094184", "5712");
        erzNamesToNumericIdDict.put("ERZ019940", "193");
        erzNamesToNumericIdDict.put("ERZ015351", "11521");
        erzNamesToNumericIdDict.put("ERZX00073", "34850");
        erzNamesToNumericIdDict.put("ERZ017130", "27");
        erzNamesToNumericIdDict.put("ERZ094207", "5564");
        erzNamesToNumericIdDict.put("ERZ015369", "11474");
        erzNamesToNumericIdDict.put("ERZ017122", "50");
        erzNamesToNumericIdDict.put("ERZ019958", "232");
        erzNamesToNumericIdDict.put("ERZ108740", "34705");
        erzNamesToNumericIdDict.put("ERZX00065", "34890");
        erzNamesToNumericIdDict.put("ERZ015361", "11471");
        erzNamesToNumericIdDict.put("ERZX00047", "8595");
        erzNamesToNumericIdDict.put("ERZX00053", "34892");
        erzNamesToNumericIdDict.put("ERZ094205", "5537");
        erzNamesToNumericIdDict.put("ERZ015348", "11495");
        erzNamesToNumericIdDict.put("ERZ015366", "11531");
        erzNamesToNumericIdDict.put("ERZ019941", "212");
        erzNamesToNumericIdDict.put("ERZ094204", "5542");
        erzNamesToNumericIdDict.put("ERZ094195", "5574");
        erzNamesToNumericIdDict.put("ERZ015367", "11527");
        erzNamesToNumericIdDict.put("ERZX00062", "34926");
    }

    @Deprecated
    public EvaWSServer() {
        logger.info("EvaWSServer: in 'constructor'");
    }

    public EvaWSServer(@Context UriInfo uriInfo, @Context HttpServletRequest hsr) {
        this.uriInfo = uriInfo;
        this.httpServletRequest = hsr;

        this.startTime = System.currentTimeMillis();
        this.queryResponse = new QueryResponse();
        this.queryOptions = new QueryOptions();

        logger.info("EvaWSServer: in 'constructor'");

        // TODO: temporary solution until the data is fixed
        translateFileIds = true;
        translateStudyIds = true;
    }

    protected void checkParams() throws VersionException, SpeciesException {
        // TODO A Version and Species checker must be implemented
        if (version == null || !version.equals("v1")) {
            throw new VersionException("Version not valid: '" + version + "'");
        }
        if (species == null || species.isEmpty()/*|| !isValidSpecies(species)*/) {
            throw new SpeciesException("Species not valid: '" + species + "'");
        }

        MultivaluedMap<String, String> multivaluedMap = uriInfo.getQueryParameters();
        queryOptions.put("species", species);

        queryOptions.put("metadata", (multivaluedMap.get("metadata") != null) ? multivaluedMap.get("metadata").get(0).equals("true") : true);
        queryOptions.put("exclude", (exclude != null && !exclude.equals("")) ? Splitter.on(",").splitToList(exclude) : null);
        queryOptions.put("include", (include != null && !include.equals("")) ? Splitter.on(",").splitToList(include) : null);
        queryOptions.put("limit", (limit > 0) ? limit : -1);
        queryOptions.put("skip", (skip > 0) ? skip : -1);
        queryOptions.put("count", (count != null && !count.equals("")) ? Boolean.parseBoolean(count) : false);
    }

    protected Response createOkResponse(Object obj) {
        endTime = System.currentTimeMillis() - startTime;
        queryResponse.setTime(new Long(endTime - startTime).intValue());
        queryResponse.setApiVersion(version);
        queryResponse.setQueryOptions(queryOptions);
        
        // Guarantee that the QueryResponse object contains a coll of results
        List coll;
        if (obj instanceof List) {
            coll = (List) obj;
        } else {
            coll = new ArrayList();
            coll.add(obj);
        }
        queryResponse.setResponse(coll);

        try {
            return Response.ok(jsonObjectWriter.writeValueAsString(queryResponse), MediaType.APPLICATION_JSON_TYPE).build();
        } catch (JsonProcessingException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex).build();
        }
    }

    protected Response createUserErrorResponse(Object obj) {
        endTime = System.currentTimeMillis() - startTime;
        queryResponse.setTime(new Long(endTime - startTime).intValue());
        queryResponse.setApiVersion(version);
        queryResponse.setQueryOptions(queryOptions);
        queryResponse.setError(obj.toString());
        
        return Response.status(Response.Status.BAD_REQUEST).entity(queryResponse).build();
    }
    
    protected Response createErrorResponse(Object obj) {
        endTime = System.currentTimeMillis() - startTime;
        queryResponse.setTime(new Long(endTime - startTime).intValue());
        queryResponse.setApiVersion(version);
        queryResponse.setQueryOptions(queryOptions);
        queryResponse.setError(obj.toString());
        
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(queryResponse).build();
    }

    protected Response createJsonResponse(Object object) {
        return Response.ok(object).build();
    }
    
    protected Response createJsonUserErrorResponse(Object object) {
        return Response.status(Response.Status.BAD_REQUEST).entity(object).build();
    }
    
    protected Response createJsonErrorResponse(Object object) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(object).build();
    }

    protected List<QueryResult<Variant>> translateFileIds(List<QueryResult<Variant>> variantQueryResults) {
        for (QueryResult<Variant> variantQueryResult : variantQueryResults) {
            translateFileIds(variantQueryResult);
        }

        return variantQueryResults;
    }

    protected QueryResult<Variant> translateFileIds(QueryResult<Variant> variantQueryResult) {
        if (translateFileIds || translateStudyIds) {
            for (Variant variant : variantQueryResult.getResult()) {
                for (VariantSourceEntry variantSource : variant.getSourceEntries().values()) {
                    translateFileId(translateFileIds, variantSource);
                    translateStudyId(translateStudyIds, variantSource);
                }
            }
        }

        return variantQueryResult;
    }

    private void translateStudyId(boolean translateStudyIds, VariantSourceEntry variantSource) {
        if (translateStudyIds) {
            String translatedStudyId = studyDict.get(variantSource.getStudyId());
            if (translatedStudyId != null) {
                variantSource.setStudyId(translatedStudyId);
            }
        }
    }

    protected QueryResult translateFileIdsInVariantSource(QueryResult<VariantSource> variantSourceQueryResult) {
        if (translateFileIds) {
            for (VariantSource variantSource : variantSourceQueryResult.getResult()) {
                String translatedFileId = erzIdsDict.get(variantSource.getFileId());
                if (translatedFileId != null) {
                    variantSource.setFileId(translatedFileId);
                }
            }
        }
        return variantSourceQueryResult;
    }

    private void translateFileId(boolean translateFileIds, VariantSourceEntry variantSource) {
        if (translateFileIds) {
            String translatedFileId = erzIdsDict.get(variantSource.getFileId());
            if (translatedFileId != null) {
                variantSource.setFileId(translatedFileId);
            }
        }
    }

//    private Response buildResponse(Response.ResponseBuilder responseBuilder) {
//        return responseBuilder.header("Access-Control-Allow-Origin", "*")
//                .header("Access-Control-Allow-Headers", "x-requested-with, content-type, accept")
//                .header("Access-Control-Allow-Methods", "POST, GET, OPTIONS").build();
//    }
    
}
