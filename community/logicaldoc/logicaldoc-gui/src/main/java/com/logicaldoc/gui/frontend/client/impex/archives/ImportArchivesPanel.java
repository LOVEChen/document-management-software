package com.logicaldoc.gui.frontend.client.impex.archives;

import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.frontend.client.administration.AdminPanel;
import com.smartgwt.client.widgets.tab.Tab;

/**
 * Panel showing export archives control panel
 * 
 * @author Marco Meschieri - Logical Objects
 * @since 6.0
 */
public class ImportArchivesPanel extends AdminPanel {

	public ImportArchivesPanel() {
		super("importarchives");

		body.setMembers(new ImportArchivesList());

		Tab bundlesTab = new Tab(I18N.message("incomingbundles"));
		bundlesTab.setPane(new ImportFoldersList(this));
		tabs.addTab(bundlesTab);
	}

	public void onConfirmImportBundle() {
		body.setMembers(new ImportArchivesList());
		tabs.selectTab(0);
	}
}