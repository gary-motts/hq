package org.hyperic.hq.ui.action.portlet.metricviewer;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionRedirect;
import org.apache.tiles.AttributeContext;
import org.apache.tiles.context.TilesRequestContext;
import org.apache.tiles.preparer.ViewPreparer;
import org.hyperic.hq.bizapp.shared.AppdefBoss;
import org.hyperic.hq.bizapp.shared.AuthzBoss;
import org.hyperic.hq.bizapp.shared.MeasurementBoss;
import org.hyperic.hq.ui.Constants;
import org.hyperic.hq.ui.WebUser;
import org.hyperic.hq.ui.action.BaseActionNG;
import org.hyperic.hq.ui.server.session.DashboardConfig;
import org.hyperic.hq.ui.shared.DashboardManager;
import org.hyperic.hq.ui.util.SessionUtils;
import org.hyperic.util.config.ConfigResponse;
import org.springframework.stereotype.Component;


@Component(value = "metricViewerViewActionNG")
public class ViewActionNG extends BaseActionNG implements ViewPreparer {

	private final Log log = LogFactory.getLog(ViewActionNG.class);
	
	@Resource
    private AuthzBoss authzBoss;
	@Resource
    private MeasurementBoss measurementBoss;
	@Resource
    private AppdefBoss appdefBoss;
	@Resource
    private DashboardManager dashboardManager;
	
	public void execute(TilesRequestContext tilesContext,
			AttributeContext attributeContext) {
		this.request = getServletRequest();
        HttpSession session = request.getSession();
        WebUser user = SessionUtils.getWebUser(session);
        DashboardConfig dashConfig = dashboardManager.findDashboard((Integer) session
            .getAttribute(Constants.SELECTED_DASHBOARD_ID), user, authzBoss);
        
        ConfigResponse dashPrefs = dashConfig.getConfig();
        
        String titleKey = PropertiesFormNG.TITLE;
		request.setAttribute("titleDescription", dashPrefs.getValue(titleKey, ""));

	}

}
