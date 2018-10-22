package com.dotcms.plugin.aop.rest;

import java.util.Map;

import static com.dotcms.util.CollectionsUtils.entry;
import static com.dotcms.util.CollectionsUtils.mapEntries;

public class UpgradeConfig {

    public final static Map<String, String> countSQL =
            mapEntries(
                    entry("contentletCount",    "select count(*) as count from contentlet"),
                    entry("workflowTaskCount",  "select count(*) as count from workflow_task"),
                    entry("htmlPageCount",      "select count(*) as count from htmlpage"),
                    entry("fileAssetTaskCount", "select count(identifier) as count from file_asset")
            );

}
