package com.logicaldoc.core.security;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logicaldoc.core.PersistentObject;
import com.logicaldoc.core.security.dao.GroupDAO;
import com.logicaldoc.util.Context;
import com.logicaldoc.util.LocaleUtil;
import com.logicaldoc.util.crypt.CryptUtil;

/**
 * This class represents a user. A user can be member of any number of groups,
 * but it is always member of a special group named '_user_'+id. When a new user
 * is created this special group of type 'user' is also created.
 * 
 * @author Michael Scholz
 * @author Marco Meschieri
 * @version 1.0
 */
public class User extends PersistentObject implements Serializable {

	protected Logger log = LoggerFactory.getLogger(User.class);

	public static int TYPE_DEFAULT = 0;

	public static int TYPE_SYSTEM = 1;

	public static int SOURCE_DEFAULT = 0;

	public static int SOURCE_LDAP = 1;

	public static int SOURCE_ACTIVE_DIRECTORY = 2;

	public static final long USERID_ADMIN = 1;

	private static final long serialVersionUID = 8093874904302301982L;

	private String username = "";

	private String password = "";

	private String passwordmd4 = "";

	private String name = "";

	private String firstName = "";

	private String street = "";

	private String postalcode = "";

	private String city = "";

	private String country = "";

	private String state = "";

	private String language = "";

	private String email = "";

	/**
	 * A simple text to be used as a signature in the footer of the outgoing
	 * emails
	 */
	private String emailSignature;

	private String email2 = "";

	private String emailSignature2;

	private String telephone = "";

	private String telephone2 = "";

	private int type = TYPE_DEFAULT;

	private Set<Group> groups = new HashSet<Group>();

	private long[] groupIds;

	private String[] groupNames;

	private int enabled = 1;

	// The last time the password was changed
	private Date passwordChanged = new Date();

	// If the password expires or not
	private int passwordExpires = 0;

	// If the password already expired
	private int passwordExpired = 0;

	// Only for GUI
	private String repass;

	private int source = 0;

	private long quota = -1;

	private Integer welcomeScreen = 1520;

	private String ipWhiteList;

	private String ipBlackList;

	private String decodedPassword;

	/**
	 * When the certificate expires
	 */
	private Date certExpire = null;

	/**
	 * The distinguished name of the certificate
	 */
	private String certDN = null;

	private Long defaultWorkspace;

	/**
	 * The second factor authenticator to use
	 */
	private String secondFactor;

	/**
	 * A key used by the second factor authenticator
	 */
	private String key;

	protected Set<UserGroup> userGroups = new HashSet<UserGroup>();

	public User() {
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getRepass() {
		return repass;
	}

	public void setRepass(String repass) {
		this.repass = repass;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getName() {
		return name;
	}

	public String getFullName() {
		String fullName = getFirstName();
		if (fullName != null && getName() != null)
			fullName += " " + getName();
		if (fullName == null && getName() != null)
			fullName = getName();
		if (fullName == null)
			fullName = getUsername();
		return fullName;
	}

	public String getInitials() {
		StringBuffer sb = new StringBuffer();
		String fullName = getFullName();
		String[] tokens = fullName.split(" ");
		for (String token : tokens) {
			if (!token.trim().isEmpty())
				sb.append(token.trim().toUpperCase().substring(0, 1));
		}
		return sb.toString();
	}

	public String getFirstName() {
		return firstName;
	}

	public String getStreet() {
		return street;
	}

	public String getPostalcode() {
		return postalcode;
	}

	public String getCity() {
		return city;
	}

	public String getCountry() {
		return country;
	}

	public String getLanguage() {
		return language;
	}

	public String getEmail() {
		return email;
	}

	public String getTelephone() {
		return telephone;
	}

	public long[] getGroupIds() {
		if (groupIds == null)
			initGroupIdsAndNames();
		return groupIds;
	}

	public String[] getGroupNames() {
		if (groupNames == null)
			initGroupIdsAndNames();
		return groupNames;
	}

	public boolean isMemberOf(String groupName) {
		String[] names = getGroupNames();
		for (int i = 0; i < names.length; i++) {
			if (groupName.equals(names[i]))
				return true;
		}
		return false;
	}

	public void addGroup(Group group) {
		if (group == null)
			return;

		if (!getGroups().contains(group))
			getGroups().add(group);

		UserGroup ug = new UserGroup(group.getId());
		if (!getUserGroups().contains(ug))
			getUserGroups().add(ug);
	}

	public void removeGroup(long groupId) {
		Iterator<UserGroup> iter = getUserGroups().iterator();
		while (iter.hasNext()) {
			UserGroup p = iter.next();
			if (p.getGroupId() == groupId)
				iter.remove();
		}

		Iterator<Group> iter2 = getGroups().iterator();
		while (iter2.hasNext()) {
			Group p = iter2.next();
			if (p.getId() == groupId)
				iter2.remove();
		}
	}

	/**
	 * Removes the user from a all groups except it's user's own group
	 */
	public void removeAllGroups() {
		Iterator<Group> iter = getGroups().iterator();
		while (iter.hasNext()) {
			Group p = iter.next();
			if (!getUserGroupName().equals(p.getName())) {
				iter.remove();
				getUserGroups().remove(new UserGroup(p.getId()));
			}
		}
	}

	public void setUsername(String uname) {
		username = uname;
	}

	public void setPassword(String pwd) {
		password = pwd;
	}

	/**
	 * Sets the password and encode it
	 * 
	 * @param pwd The password in readable format
	 */
	public void setDecodedPassword(String pwd) {
		if (org.apache.commons.lang.StringUtils.isNotEmpty(pwd)) {
			decodedPassword = pwd;
			password = CryptUtil.cryptString(pwd);
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public void setStreet(String str) {
		street = str;
	}

	public void setPostalcode(String pc) {
		postalcode = pc;
	}

	public void setCity(String ct) {
		city = ct;
	}

	public void setCountry(String cnt) {
		country = cnt;
	}

	public void setLanguage(String lang) {
		language = lang;
	}

	public void setEmail(String mail) {
		email = mail;
	}

	public void setTelephone(String phone) {
		telephone = phone;
	}

	private void initGroupIdsAndNames() {
		try {
			groupIds = new long[userGroups.size()];
			int i = 0;
			for (UserGroup ug : userGroups)
				groupIds[i++] = ug.getGroupId();

			groupNames = new String[groups.size()];

			Iterator<Group> iter = groups.iterator();
			i = 0;
			while (iter.hasNext()) {
				Group ug = iter.next();
				if (ug.getDeleted() == 1)
					continue;
				groupNames[i] = ug.getName();
				i++;
			}
		} catch (Throwable e) {

		}
	}

	public void reset() {
		username = "";
		password = "";
		passwordmd4 = "";
		name = "";
		firstName = "";
		street = "";
		postalcode = "";
		city = "";
		country = "";
		language = "";
		email = "";
		telephone = "";
		groups = new HashSet<Group>();
		groupIds = null;
		passwordExpires = 0;
	}

	public String toString() {
		return getUsername();
	}

	public Set<Group> getGroups() {
		return groups;
	}

	public void setGroups(Set<Group> groups) {
		this.groups = groups;
	}

	/**
	 * The name of the group associated to this user, that is '_user_'+id
	 */
	public String getUserGroupName() {
		return "_user_" + getId();
	}

	/**
	 * Retrieves this user's group
	 */
	public Group getUserGroup() {
		if (getGroups() != null)
			for (Group grp : getGroups()) {
				if (grp.getName().equals(getUserGroupName()))
					return grp;
			}

		// The special group was not found in the belonging groups, this may
		// indicate a lost association between this user and his group
		log.warn("User " + username + " has lost association with his group");
		GroupDAO dao = (GroupDAO) Context.get().getBean(GroupDAO.class);
		Group group = dao.findByName(getUserGroupName(), getTenantId());
		if (group == null)
			log.warn("User " + username + " doesn't have his user group " + getUserGroupName());
		return group;
	}

	public int getEnabled() {
		return enabled;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getTelephone2() {
		return telephone2;
	}

	public void setTelephone2(String telephone2) {
		this.telephone2 = telephone2;
	}

	public Locale getLocale() {
		return LocaleUtil.toLocale(getLanguage());
	}

	public void setLocale(Locale locale) {
		setLanguage(locale.toString());
	}

	/**
	 * When the password was modified
	 */
	public Date getPasswordChanged() {
		return passwordChanged;
	}

	public void setPasswordChanged(Date passwordChanged) {
		this.passwordChanged = passwordChanged;
	}

	/**
	 * If the password expires or not
	 */
	public int getPasswordExpires() {
		return passwordExpires;
	}

	public void setPasswordExpires(int passwordExpires) {
		this.passwordExpires = passwordExpires;
	}

	/**
	 * The source from which the user has been created
	 * 
	 * @see User#SOURCE_DEFAULT
	 * @see User#SOURCE_LDAP
	 * @see User#SOURCE_ACTIVE_DIRECTORY
	 */
	public int getSource() {
		return source;
	}

	public void setSource(int source) {
		this.source = source;
	}

	public long getQuota() {
		return quota;
	}

	public void setQuota(long quota) {
		this.quota = quota;
	}

	public Integer getWelcomeScreen() {
		return welcomeScreen;
	}

	public void setWelcomeScreen(Integer welcomeScreen) {
		this.welcomeScreen = welcomeScreen;
	}

	public String getIpWhiteList() {
		return ipWhiteList;
	}

	public void setIpWhiteList(String ipWhiteList) {
		this.ipWhiteList = ipWhiteList;
	}

	public String getIpBlackList() {
		return ipBlackList;
	}

	public void setIpBlackList(String ipBlackList) {
		this.ipBlackList = ipBlackList;
	}

	public int getPasswordExpired() {
		return passwordExpired;
	}

	public void setPasswordExpired(int passwordExpired) {
		this.passwordExpired = passwordExpired;
	}

	public String getPasswordmd4() {
		return passwordmd4;
	}

	public void setPasswordmd4(String passwordmd4) {
		this.passwordmd4 = passwordmd4;
	}

	public String getDecodedPassword() {
		return decodedPassword;
	}

	public String getEmailSignature() {
		return emailSignature;
	}

	public void setEmailSignature(String emailSignature) {
		this.emailSignature = emailSignature;
	}

	public void clearPassword() {
		this.password = null;
		this.passwordmd4 = null;
	}

	public Long getDefaultWorkspace() {
		return defaultWorkspace;
	}

	public void setDefaultWorkspace(Long defaultWorkspace) {
		this.defaultWorkspace = defaultWorkspace;
	}

	public String getEmail2() {
		return email2;
	}

	public void setEmail2(String email2) {
		this.email2 = email2;
	}

	public String getEmailSignature2() {
		return emailSignature2;
	}

	public void setEmailSignature2(String emailSignature2) {
		this.emailSignature2 = emailSignature2;
	}

	public Set<UserGroup> getUserGroups() {
		return userGroups;
	}

	public void setUserGroups(Set<UserGroup> userGroups) {
		this.userGroups = userGroups;
	}

	public Date getCertExpire() {
		return certExpire;
	}

	public void setCertExpire(Date certExpire) {
		this.certExpire = certExpire;
	}

	public String getCertDN() {
		return certDN;
	}

	public void setCertDN(String certDN) {
		this.certDN = certDN;
	}

	public String getSecondFactor() {
		return secondFactor;
	}

	public void setSecondFactor(String secondFactor) {
		this.secondFactor = secondFactor;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}
}