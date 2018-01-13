package com.logicaldoc.gui.frontend.client.security.twofactorsauth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.logicaldoc.gui.common.client.Constants;
import com.logicaldoc.gui.common.client.Session;
import com.logicaldoc.gui.common.client.beans.GUIParameter;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.Log;
import com.logicaldoc.gui.common.client.util.ItemFactory;
import com.logicaldoc.gui.common.client.util.Util;
import com.logicaldoc.gui.frontend.client.administration.AdminPanel;
import com.logicaldoc.gui.frontend.client.services.SettingService;
import com.smartgwt.client.types.TitleOrientation;
import com.smartgwt.client.widgets.IButton;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.ValuesManager;
import com.smartgwt.client.widgets.form.fields.RadioGroupItem;
import com.smartgwt.client.widgets.layout.VLayout;

/**
 * This panel shows general settings about the 2FA.
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 7.7.3
 */
public class TwoFactorsAuthenticationSettings extends AdminPanel {

	private ValuesManager vm = new ValuesManager();

	public TwoFactorsAuthenticationSettings() {
		super("twofactorsauth");

		SettingService.Instance.get().loadSettingsByNames(new String[] { Session.get().getTenantName() + ".2fa.*" },
				new AsyncCallback<GUIParameter[]>() {

					@Override
					public void onFailure(Throwable caught) {
						Log.serverError(caught);
					}

					@Override
					public void onSuccess(GUIParameter[] params) {
						init(params);
					}
				});
	}

	private void init(GUIParameter[] parameters) {
		DynamicForm form = new DynamicForm();
		form.setWidth(1);
		form.setValuesManager(vm);
		form.setTitleOrientation(TitleOrientation.LEFT);
		form.setNumCols(1);

		Map<String, String> settings = Util.convertToMap(parameters);
		final RadioGroupItem enable2fa = ItemFactory.newBooleanSelector("enable2fa", I18N.message("enable2fa"));
		enable2fa.setValue("true".equals(settings.get("enabled")) ? "yes" : "no");
		enable2fa.setWrapTitle(false);
		enable2fa.setWrap(false);
		enable2fa.setRequired(true);
		enable2fa.setDisabled(Session.get().isDemo());
		form.setFields(enable2fa);

		/*
		 * GoogleAuthenticator section
		 */
		DynamicForm googleForm = new DynamicForm();
		googleForm.setValuesManager(vm);
		googleForm.setTitleOrientation(TitleOrientation.TOP);
		googleForm.setIsGroup(true);
		googleForm.setGroupTitle("Google Authenticator");
		googleForm.setNumCols(1);

		final RadioGroupItem enableGoolge = ItemFactory.newBooleanSelector("enableGoolge",
				I18N.message("enablegoogleauthenticator"));
		enableGoolge.setValue("true".equals(settings.get(Constants.TWOFA_GOOGLE_AUTHENTICATOR + ".enabled")) ? "yes"
				: "no");
		enableGoolge.setWrapTitle(false);
		enableGoolge.setWrap(false);
		enableGoolge.setRequired(true);
		enableGoolge.setDisabled(Session.get().isDemo());
		googleForm.setFields(enableGoolge);

		/*
		 * Yubikey section
		 */
		DynamicForm yubikeyForm = new DynamicForm();
		yubikeyForm.setValuesManager(vm);
		yubikeyForm.setTitleOrientation(TitleOrientation.TOP);
		yubikeyForm.setIsGroup(true);
		yubikeyForm.setGroupTitle("YubiKey");
		yubikeyForm.setNumCols(1);

		final RadioGroupItem enableYubikey = ItemFactory.newBooleanSelector("enableYubikey",
				I18N.message("enableyubikey"));
		enableYubikey.setValue("true".equals(settings.get("yubikey.enabled")) ? "yes" : "no");
		enableYubikey.setWrapTitle(false);
		enableYubikey.setWrap(false);
		enableYubikey.setRequired(true);
		enableYubikey.setDisabled(Session.get().isDemo());
		yubikeyForm.setFields(enableYubikey);

		IButton save = new IButton();
		save.setTitle(I18N.message("save"));
		save.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				if (vm.validate()) {
					String tenant = Session.get().getTenantName();
					final List<GUIParameter> params = new ArrayList<GUIParameter>();
					params.add(new GUIParameter(tenant + ".2fa.enabled",
							vm.getValueAsString("enable2fa").equals("yes") ? "true" : "false"));
					params.add(new GUIParameter(tenant + ".2fa." + Constants.TWOFA_GOOGLE_AUTHENTICATOR + ".enabled",
							vm.getValueAsString("enableGoolge").equals("yes") ? "true" : "false"));
					params.add(new GUIParameter(tenant + ".2fa." + Constants.TWOFA_YUBIKEY + ".enabled",
							vm.getValueAsString("enableYubikey").equals("yes") ? "true" : "false"));
					SettingService.Instance.get().saveSettings(params.toArray(new GUIParameter[0]),
							new AsyncCallback<Void>() {

								@Override
								public void onFailure(Throwable caught) {
									Log.serverError(caught);
								}

								@Override
								public void onSuccess(Void arg) {
									Session.get().updateConfig(params);
									Log.info(I18N.message("settingssaved"), null);
								}
							});
				}
			}
		});

		VLayout panel = new VLayout();
		panel.setWidth100();
		panel.setMembers(form, googleForm, yubikeyForm);

		body.setMembers(panel);
		addMember(save);
	}
}