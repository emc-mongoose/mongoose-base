package com.emc.mongoose.base.load.step.client;

import com.emc.mongoose.base.load.step.service.file.FileManagerService;
import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.svc.ServiceUtil;
import org.apache.logging.log4j.Level;

public interface FileManagerClient {
	static FileManagerService resolve(final String nodeAddrWithPort)
			throws java.rmi.NotBoundException, java.rmi.RemoteException, java.net.URISyntaxException,
			java.net.MalformedURLException {
			return ServiceUtil.resolve(nodeAddrWithPort, FileManagerService.SVC_NAME);
	}
}
