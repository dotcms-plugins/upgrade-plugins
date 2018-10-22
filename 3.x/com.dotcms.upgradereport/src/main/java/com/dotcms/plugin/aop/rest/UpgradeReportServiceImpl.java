package com.dotcms.plugin.aop.rest;

import com.dotcms.cmsmaintenance.ajax.ThreadMonitorTool;
import com.dotcms.content.elasticsearch.business.ContentletIndexAPI;
import com.dotcms.content.elasticsearch.business.ESIndexAPI;
import com.dotcms.enterprise.LicenseUtil;
import com.dotcms.repackage.com.google.common.collect.ImmutableList;
import com.dotcms.repackage.com.google.common.collect.ImmutableMap;
import com.dotcms.repackage.com.google.common.collect.ImmutableSet;
import com.dotcms.repackage.org.apache.commons.lang.builder.ToStringBuilder;
import com.dotcms.repackage.org.osgi.framework.Bundle;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.plugin.business.PluginAPI;
import com.dotmarketing.plugin.model.Plugin;
import com.dotmarketing.util.*;
import com.liferay.portal.util.ReleaseInfo;
import com.liferay.util.StringPool;
import org.elasticsearch.action.admin.cluster.health.ClusterIndexHealth;
import org.elasticsearch.action.admin.indices.status.IndexStatus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class UpgradeReportServiceImpl implements UpgradeReportService {

    private static final String SYSTEM_BUNDLE_PREFIX = "com.dotcms.repackage.";

    private final Set<String> systemBundles = ImmutableSet.copyOf(Arrays.asList(
            SYSTEM_BUNDLE_PREFIX + "org.apache.felix.http.bundle",
            SYSTEM_BUNDLE_PREFIX + "org.apache.felix.gogo.shell",
            SYSTEM_BUNDLE_PREFIX + "org.apache.felix.bundlerepository",
            SYSTEM_BUNDLE_PREFIX + "org.apache.felix.framework",
            SYSTEM_BUNDLE_PREFIX + "org.apache.felix.fileinstall",
            SYSTEM_BUNDLE_PREFIX + "org.apache.felix.gogo.command",
            SYSTEM_BUNDLE_PREFIX + "org.apache.felix.gogo.runtime",
            SYSTEM_BUNDLE_PREFIX + "osgi.cmpn",
            SYSTEM_BUNDLE_PREFIX + "osgi.core",
            SYSTEM_BUNDLE_PREFIX + "org.apache.tika.core",
            SYSTEM_BUNDLE_PREFIX + "org.apache.tika.bundle",
            "slf4j.simple",
            "slf4j.api",
            "jcl.over.slf4j",
            SYSTEM_BUNDLE_PREFIX + "com.dotcms.tika",
            SYSTEM_BUNDLE_PREFIX + "org.apache.felix.http.api",
            SYSTEM_BUNDLE_PREFIX + "org.apache.felix.configadmin"
    ));
    private final PluginAPI pluginAPI = APILocator.getPluginAPI();
    private final Map<String, Object> zeroResultsMap = ImmutableMap.of("count", Integer.valueOf(0));

    @Override
    public Map<String, Object> createReport() throws Exception {

        final Map<String, Object> resultMap = new LinkedHashMap<>();
        try {
            resultMap.put("date", DateUtil.getCurrentDate());
            resultMap.put("displayServerId", LicenseUtil.getDisplayServerId());
            resultMap.put("levelName", LicenseUtil.getLevelName());
            resultMap.put("levelName", LicenseUtil.getLevelName());
            resultMap.put("level", LicenseUtil.getLevel());
            resultMap.put("version",           ReleaseInfo.getVersion());
            resultMap.put("buildDate",        ReleaseInfo.getBuildDateString());
            resultMap.put("dotDbVersion", Config.DB_VERSION);
            resultMap.put("fullDbVersion", getDbFullVersion());
            resultMap.put("dbType", DbConnectionFactory.getDBType());
            resultMap.put("countQueries", this.executeCountQueries());
            resultMap.put("staticPlugins", this.getStaticPlugins());
            resultMap.put("dynaPlugins", this.getDynaPlugins());
            resultMap.put("assets", this.getAssetSize());
            resultMap.put("cacheInfo", this.getCacheStats());
            resultMap.put("indexInfo", this.getIndexInfo());
            resultMap.put("threadDumps", new ThreadMonitorTool().getThreads());

        } finally {
            HibernateUtil.closeSession();
        }

        return resultMap;
    }

    private Object getIndexInfo() throws Exception {

        final Map<String, Object>  resultMap         = new LinkedHashMap<>();
        final Set<Object>  indicesResultSet          = new LinkedHashSet<>();
        final Set<Object>  closedResultSet           = new LinkedHashSet<>();
        final ESIndexAPI esIndexAPI                  = APILocator.getESIndexAPI();
        final ContentletIndexAPI contentletIndexAPI  = APILocator.getContentletIndexAPI();
        final List<String> currentIndex              = contentletIndexAPI.getCurrentIndex();
        final List<String> newIndex                  = contentletIndexAPI.getNewIndex();
        final List<String> indices                   = contentletIndexAPI.listDotCMSIndices();
        final List<String> closedIndices             = contentletIndexAPI.listDotCMSClosedIndices();
        final SimpleDateFormat dater                 = new SimpleDateFormat("yyyyMMddHHmmss");
        final Map<String, IndexStatus> indexInfo               = esIndexAPI.getIndicesAndStatus();
        final Map<String, ClusterIndexHealth> clusterHealthMap = esIndexAPI.getClusterHealth();

        for(final String indiceName : indices){

            final Map<String, Object> indiceMap = new LinkedHashMap<>();
            final ClusterIndexHealth health     = clusterHealthMap.get(indiceName);
            final IndexStatus status            = indexInfo.get(indiceName);
            final boolean active                = currentIndex.contains(indiceName);
            final boolean building              = newIndex.contains(indiceName);
            final String myDate                 = getDateString(dater, indiceName);

            indiceMap.put("status",   active?"Active":(building?"Building":"Nothing"));
            indiceMap.put("name",     indiceName);
            indiceMap.put("created",  UtilMethods.webifyString(myDate));
            indiceMap.put("count",    (status !=null && status.getDocs() != null)? status.getDocs().getNumDocs(): "n/a");
            indiceMap.put("shards",   (health !=null)? health.getNumberOfShards()         : "n/a");
            indiceMap.put("replicas", (health !=null)? health.getNumberOfReplicas()  : "n/a");
            indiceMap.put("size",     (status !=null)? status.getStoreSize()         : "n/a");
            indiceMap.put("health",   (health !=null)? health.getStatus().toString() : "n/a");
            indicesResultSet.add(indiceMap);
        }

        for(final String closedIndice : closedIndices) {

            final Map<String, Object> indiceMap = new LinkedHashMap<>();
            final String myDate                 = getDateString(dater, closedIndice);

            indiceMap.put("status",   "Closed");
            indiceMap.put("name",     closedIndice);
            indiceMap.put("created",  UtilMethods.webifyString(myDate));
            indiceMap.put("count",    "n/a");
            indiceMap.put("shards",   "n/a");
            indiceMap.put("replicas", "n/a");
            indiceMap.put("size",     "n/a");
            indiceMap.put("health",   "n/a");
            closedResultSet.add(indiceMap);
        }

        resultMap.put("indices",       indicesResultSet);
        resultMap.put("closedIndices", closedResultSet);

        return resultMap;
    }

    private String getDateString(SimpleDateFormat dater, String indiceName) {
        String myDate = null;
        try {
            myDate = indiceName.split("_")[1];
            final Date d = dater.parse(myDate);
            myDate = UtilMethods.dateToPrettyHTMLDate(d) + " " + UtilMethods.dateToHTMLTime(d);
        } catch (Exception e) {
        }
        return myDate;
    }

    private Object getCacheStats() {

        final Map<String, String> cacheStats = new LinkedHashMap<>();

        cacheStats.put("Total-Memory-Available", UtilMethods.prettyByteify(Runtime.getRuntime().maxMemory()));
        cacheStats.put("Memory-Allocated",       UtilMethods.prettyByteify(Runtime.getRuntime().totalMemory()));
        cacheStats.put("Filled-Memory",          UtilMethods.prettyByteify(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        cacheStats.put("Free-Memory",            UtilMethods.prettyByteify(Runtime.getRuntime().freeMemory()));

        return cacheStats;
    }

    /**
     * Method to get the Database Full Version
     * @return Database Version (Major.Minor)
     */
    public static float getDbFullVersion() {
        try {
            Connection con = DbConnectionFactory.getConnection();
            DatabaseMetaData meta = con.getMetaData();
            String version = "%d.%d";
            version = String.format(version, meta.getDatabaseMajorVersion(), meta.getDatabaseMinorVersion());
            return Float.parseFloat(version);
        } catch (SQLException e) {
            Logger.error(DbConnectionFactory.class,
                    "---------- DBConnectionFactory: Error getting DB Full version " + "---------------", e);
            throw new DotRuntimeException(e.toString());
        }
    }

    private static String [] filteredNames = new String[] {"bundles","dotgenerated","integrity","server","timemachine","tmp_upload"};

    private Object getAssetSize() throws IOException {

        final String pathAsset = APILocator.getFileAssetAPI().getRealAssetsRootPath();
        final File   fileAsset = new File(pathAsset);
        Logger.info(this, "pathAsset: " + pathAsset);

        return (fileAsset.exists() && fileAsset.canRead())?
                Files.walk(fileAsset.toPath()).map(Path::toFile).filter
                        (file -> file.exists() && file.canRead() && !file.isHidden() && !this.filterByName(file)).mapToLong(File::length).sum():
                Long.valueOf(0);
    }

    private boolean filterByName (final File file) {

        boolean isFilteredByName = false;

        for (final String filteredName : filteredNames) {

            isFilteredByName |= file.getName().contains(filteredName);
        }

        return isFilteredByName;
    }


    private Map<String, Object> executeCountQueries() throws DotDataException {

        final Map<String, String> countSqlQueries = UpgradeConfig.countSQL;
        final Map<String, Object> countSqlQueriesResults = new LinkedHashMap<>();

        for (final Map.Entry<String, String> queryEntry : countSqlQueries.entrySet()) {

            try {

                final DotConnect dotConnect = new DotConnect();
                dotConnect.setSQL(queryEntry.getValue());
                final List<Map<String, Object>> results = dotConnect.loadObjectResults();
                countSqlQueriesResults.put(queryEntry.getKey(),
                        results.stream().findFirst().orElse(this.zeroResultsMap));
            } catch (Exception e) {

                countSqlQueriesResults.put(queryEntry.getKey(), e.getMessage());
            }
        }

        return countSqlQueriesResults;
    }

    private List<String> getStaticPlugins() throws DotDataException {

        final List<Plugin> plugins = this.pluginAPI.findPlugins();
        return (UtilMethods.isSet(plugins)) ?
                plugins.stream().map(ToStringBuilder::reflectionToString).collect(Collectors.toList()) :
                Collections.emptyList();
    }

    private List<Map<String, Object>> getDynaPlugins() {

        final Bundle[] installedBundles = OSGIUtil.getInstance().getBundleContext().getBundles();
        final ImmutableList.Builder<Map<String, Object>> dynaPlugins = new ImmutableList.Builder<>();

        for (final Bundle bundle : installedBundles) {

            if (systemBundles.contains(bundle.getSymbolicName())) {
                continue;
            }

            //Getting the jar file name
            final String separator = (bundle.getLocation().contains(StringPool.SLASH)) ? StringPool.SLASH : File.separator;
            final String jarFile = bundle.getLocation().contains(separator) ? bundle.getLocation().substring(bundle.getLocation().lastIndexOf(separator) + 1) : "System";
            //Build the version string
            final String version = bundle.getVersion().getMajor() + "." + bundle.getVersion().getMinor() + "." + bundle.getVersion().getMicro();

            //Reading and setting bundle information
            final ImmutableMap.Builder<String, Object> pluginInfoMap = new ImmutableMap.Builder<>();
            pluginInfoMap.put("bundleId", bundle.getBundleId());
            pluginInfoMap.put("symbolicName", bundle.getSymbolicName());
            pluginInfoMap.put("location", bundle.getLocation());
            pluginInfoMap.put("jarFile", jarFile);
            pluginInfoMap.put("state", bundle.getState());
            pluginInfoMap.put("version", version);
            pluginInfoMap.put("separator", separator);

            dynaPlugins.add(pluginInfoMap.build());
        }

        return dynaPlugins.build();
    }

}
