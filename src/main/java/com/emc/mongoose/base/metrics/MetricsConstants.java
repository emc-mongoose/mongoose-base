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
	String META_DATA_STEP_ID = "load_step_id";
	String META_DATA_OP_TYPE = "load_op_type";
	String META_DATA_LIMIT_CONC = "storage_driver_limit_concurrency";
	String META_DATA_ITEM_DATA_SIZE = "item_data_size";
	String META_DATA_START_TIME = "start_time";
	String META_DATA_NODE_LIST = "node_list";
	String META_DATA_COMMENT = "user_comment";
	String META_DATA_RUN_ID = "run_id";
	//
	String[] METRIC_LABELS = {
			META_DATA_OP_TYPE,
			META_DATA_OP_TYPE,
			META_DATA_LIMIT_CONC,
			META_DATA_ITEM_DATA_SIZE,
			META_DATA_START_TIME,
			META_DATA_NODE_LIST,
			META_DATA_COMMENT,
			META_DATA_RUN_ID
	};
	String METRIC_FORMAT = Constants.APP_NAME + "_%s"; // appName_metricName<_aggregationType>
}
