package com.emc.mongoose.base.metrics;

import com.emc.mongoose.base.Constants;

public interface MetricsConstants {

	String METRIC_NAME_DUR = "duration";
	String METRIC_NAME_LAT = "latency";
	String METRIC_NAME_CONC = "concurrency";
	String METRIC_NAME_SUCC = "success_op";
	String METRIC_NAME_FAIL = "failed_op";
	String METRIC_NAME_BYTE = "byte";
	String METRIC_NAME_TIME = "elapsed_time";
	//
	String METADATA_STEP_ID = "load_step_id";
	String METADATA_OP_TYPE = "load_op_type";
	String METADATA_LIMIT_CONC = "storage_driver_limit_concurrency";
	String METADATA_ITEM_DATA_SIZE = "item_data_size";
	String METADATA_START_TIME = "start_time";
	String METADATA_NODE_LIST = "node_list";
	String METADATA_COMMENT = "user_comment";
	String METADATA_RUN_ID = "run_id";
	//
	String[] METRIC_LABELS = {
			METADATA_STEP_ID,
			METADATA_OP_TYPE,
			METADATA_LIMIT_CONC,
			METADATA_ITEM_DATA_SIZE,
			METADATA_START_TIME,
			METADATA_NODE_LIST,
			METADATA_COMMENT,
			METADATA_RUN_ID
	};
	String METRIC_FORMAT = Constants.APP_NAME + "_%s"; // appName_metricName<_aggregationType>
}
