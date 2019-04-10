var jSystem = java.lang.System
var jStdOut = jSystem.out
var jThread = java.lang.Thread

var startTimeMillis = jSystem.currentTimeMillis();

function sleepUntilTime(timeMillis) {
	var currentTimeMillis;
	var remainingMillis;
	do {
		currentTimeMillis = jSystem.currentTimeMillis();
		remainingMillis = timeMillis - currentTimeMillis + startTimeMillis;
		if(remainingMillis > 0) {
			jThread.sleep(remainingMillis / 2);
		} else {
			break;
		}
	} while(true);
}

sleepUntilTime(1000);

Load
	.config({
		"item": {
			"naming": {
				"prefix": "foo",
				"seed": null,
			}
		}
	})
	.run();
