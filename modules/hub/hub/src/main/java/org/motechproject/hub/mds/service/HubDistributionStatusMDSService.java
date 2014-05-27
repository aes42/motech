package org.motechproject.hub.mds.service;

import java.util.List;

import org.motechproject.hub.mds.HubDistributionStatus;
import org.motechproject.hub.mds.HubSubscription;
import org.motechproject.hub.mds.HubSubscriptionStatus;
import org.motechproject.mds.annotations.Lookup;
import org.motechproject.mds.annotations.LookupField;
import org.motechproject.mds.service.MotechDataService;

public interface HubDistributionStatusMDSService extends MotechDataService<HubDistributionStatus> {

	@Lookup(name = "By Status")
	 List<HubDistributionStatus> findByStatus(@LookupField(name = "distributionStatusId") int statusId);
}