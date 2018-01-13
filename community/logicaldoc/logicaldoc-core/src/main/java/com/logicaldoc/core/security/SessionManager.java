package com.logicaldoc.core.security;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.logicaldoc.core.security.authentication.AuthenticationChain;
import com.logicaldoc.core.security.authentication.AuthenticationException;
import com.logicaldoc.core.security.dao.SessionDAO;
import com.logicaldoc.core.security.spring.LDAuthenticationToken;
import com.logicaldoc.core.security.spring.LDSecurityContextRepository;
import com.logicaldoc.util.Context;

/**
 * Repository of all current user sessions.
 * 
 * @author Marco Meschieri - Logical Objects
 * @since 4.6
 */
public class SessionManager extends ConcurrentHashMap<String, Session> {

	public static final String COOKIE_SID = "ldoc-sid";

	public static final String PARAM_SID = "sid";

	private static Logger log = LoggerFactory.getLogger(SessionManager.class);

	private static final long serialVersionUID = 1L;

	// The maximum number of closed session maintained in memory
	private static int MAX_CLOSED_SESSIONS = 50;

	private AuthenticationChain authenticationChain;

	private SessionDAO sessionDao;

	private SessionTimeoutWatchDog timeoutWatchDog = new SessionTimeoutWatchDog();

	private SessionManager() {
		timeoutWatchDog.start();
		log.info("Starting the session timeout watchdog");
	}

	public final static SessionManager get() {
		return (SessionManager) Context.get().getBean(SessionManager.class);
	}

	/**
	 * Creates a new session by authenticated the given user and stores it in
	 * the pool of opened sessions
	 */
	public synchronized Session newSession(String username, String password, String key, Client client)
			throws AuthenticationException {
		User user = authenticationChain.authenticate(username, password, key, client);
		if (user == null)
			return null;
		else {
			Session session = new Session(user, password, key, client);
			put(session.getSid(), session);
			log.warn("Created new session {} for user {}", session.getSid(), username);
			cleanClosedSessions();

			storeSession(session);
			return session;
		}
	}

	/**
	 * Creates a new session by authenticated the given user and stores it in
	 * the pool of opened sessions
	 */
	public synchronized Session newSession(String username, String password, Client client)
			throws AuthenticationException {
		return newSession(username, password, null, client);
	}

	private void storeSession(Session session) {
		try {
			if (session.getId() == 0L) {
				Session dbSession = (Session) session.clone();
				sessionDao.store(dbSession);
				session.setId(dbSession.getId());
			} else {
				Session dbSession = sessionDao.findById(session.getId());
				if (dbSession != null) {
					dbSession.setDeleted(session.getDeleted());
					dbSession.setLastRenew(session.getLastRenew());
					dbSession.setStatus(session.getStatus());
					sessionDao.store(dbSession);
				}
			}
		} catch (org.springframework.orm.hibernate4.HibernateOptimisticLockingFailureException fe1) {
			// May happen, forget it.
		} catch (org.springframework.orm.hibernate5.HibernateOptimisticLockingFailureException fe2) {
			// May happen, forget it.
		} catch (Throwable t) {
			log.warn(t.getMessage(), t);
		}
	}

	/**
	 * Kills an existing session
	 */
	public void kill(String sid) {
		Session session = get(sid);
		if (session != null) {
			session.setClosed();
			log.warn("Killed session " + sid);
			storeSession(session);
		}
	}

	@Override
	public Session remove(Object sid) {
		kill((String) sid);
		sessionDao.delete(get(sid).getId());
		return super.remove(sid);
	}

	/**
	 * Renews an opened session
	 * 
	 * @param sid The session to be renewed
	 */
	public void renew(String sid) {
		if (isOpen(sid)) {
			Session session = get(sid);
			if (session.getStatus() == Session.STATUS_OPEN) {
				if (session.isTimedOut()) {
					session.setExpired();
				} else {
					session.setLastRenew(new Date());
				}
			}
		}
	}

	public int getStatus(String sid) {
		Session session = get(sid);
		if (session == null)
			return -1;
		if (session.getStatus() == Session.STATUS_OPEN && session.isTimedOut()) {
			session.setExpired();
			storeSession(session);
		}
		return session.getStatus();
	}

	/**
	 * Checks if a session is valid or not. A valid session is a one that exists
	 * and is in state OPEN
	 * 
	 * @param sid The session identifier
	 * @return true only if the session exists and is OPEN
	 */
	public boolean isOpen(String sid) {
		if (sid == null)
			return false;
		return Session.STATUS_OPEN == getStatus(sid);
	}

	@Override
	public Session get(Object sid) {
		if (sid == null)
			return null;
		Session session = super.get(sid);
		return session;
	}

	/**
	 * Gets the session of the given client
	 */
	public Session getByClientId(String clientId) {
		if (clientId == null)
			return null;

		for (Session session : getSessions()) {
			if (session.getClient() != null && clientId.equals(session.getClient().getId()))
				return session;
		}

		return null;
	}

	/**
	 * Counts the total number of opened sessions
	 */
	public int countOpened() {
		return sessionDao.countSessions(null, Session.STATUS_OPEN);
	}

	/**
	 * Counts the total number of opened sessions per tenant
	 */
	public int countOpened(long tenantId) {
		return sessionDao.countSessions(tenantId, Session.STATUS_OPEN);
	}

	/**
	 * Returns the list of sessions of the current node ordered by ascending
	 * status and creation date.
	 */
	public List<Session> getSessions() {
		List<Session> sessions = new ArrayList<Session>(values());
		Collections.sort(sessions);
		return sessions;
	}

	/**
	 * Clean method that removes all closed sessions that exceed the number of
	 * MAX_CLOSED_SESSIONS
	 */
	private void cleanClosedSessions() {
		List<String> garbage = new ArrayList<String>();
		int counter = 0;
		for (Session session : getSessions()) {
			if (getStatus(session.getSid()) != Session.STATUS_OPEN)
				counter++;
			if (counter > MAX_CLOSED_SESSIONS)
				garbage.add(session.getSid());
		}
		for (String sid : garbage) {
			remove(sid);
		}
	}

	/**
	 * Gets the Session returned by <code>getSid(request)</code>
	 */
	public Session getSession(HttpServletRequest request) {
		String sid = getSessionId(request);
		if (sid == null)
			return null;
		if (isOpen(sid))
			return get(sid);
		return null;
	}

	/**
	 * Gets the Session ID specification from the current request following this
	 * lookup strategy:
	 * <ol>
	 * <li>Session attribute <code>PARAM_SID</code></li>
	 * <li>Request attribute <code>PARAM_SID</code></li>
	 * <li>Request parameter <code>PARAM_SID</code></li>
	 * <li>Cookie <code>COOKIE_SID</code></li>
	 * <li>Spring SecurityContextHolder</li>
	 * 
	 * @param request The current request to inspect
	 * @return The SID if any
	 */
	public String getSessionId(HttpServletRequest request) {
		if (request != null) {
			if (request.getSession(false) != null && request.getSession(false).getAttribute(PARAM_SID) != null)
				return (String) request.getSession(false).getAttribute(PARAM_SID);
			if (request.getAttribute(PARAM_SID) != null)
				return (String) request.getAttribute(PARAM_SID);
			if (request.getParameter(PARAM_SID) != null)
				return (String) request.getParameter(PARAM_SID);

			Cookie cookies[] = request.getCookies();
			if (cookies != null)
				for (Cookie cookie : cookies) {
					if (COOKIE_SID.equals(cookie.getName()))
						return cookie.getValue();
				}
		}

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth instanceof LDAuthenticationToken)
			return ((LDAuthenticationToken) auth).getSid();

		if (request != null) {
			Client client = buildClient(request);
			Session session = getByClientId(client.getId());
			if (session != null && isOpen(session.getSid()))
				return session.getSid();
		}

		return null;
	}

	/**
	 * Saves the session identifier in the request and session attribute
	 * <code>PARAM_SID</code> and Cookie <code>COOKIE_SID</code>
	 * 
	 * @param request
	 * @param sid
	 */
	public void saveSid(HttpServletRequest request, HttpServletResponse response, String sid) {
		request.setAttribute(PARAM_SID, sid);
		if (request.getSession(false) != null)
			request.getSession(false).setAttribute(PARAM_SID, sid);

		Cookie sidCookie = new Cookie(COOKIE_SID, sid);
		sidCookie.setHttpOnly(true);
		response.addCookie(sidCookie);
	}

	/**
	 * Removes the Sid
	 */
	public void removeSid(HttpServletRequest request) {
		if (request != null) {
			request.removeAttribute(PARAM_SID);
			if (request.getSession(false) != null)
				request.getSession(false).removeAttribute(PARAM_SID);
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null)
			SecurityContextHolder.getContext().setAuthentication(null);
	}

	/**
	 * Retrieves the session ID of the current thread execution
	 */
	public static String getCurrentSid() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof LDAuthenticationToken)
			return ((LDAuthenticationToken) auth).getSid();
		else
			return null;
	}

	public HttpSession getServletSession(String sid) {
		if (sid == null)
			return null;
		return LDSecurityContextRepository.getServletSession(sid);
	}

	/**
	 * Create a client identified using a concatenation of Basic authentication
	 * credentials and remote IP.
	 * 
	 * @param req The request to process
	 * @return The client
	 */
	public Client buildClient(HttpServletRequest req) {
		Client client = new Client(req.getRemoteAddr(), req.getRemoteHost());

		String[] credentials = getBasicCredentials(req);
		if (credentials != null)
			client.setId(String.format("%s-%s-%s", credentials[0],
					credentials[1] == null ? "0" : credentials[1].hashCode(), req.getRemoteAddr()));
		return client;

	}

	private static String[] getBasicCredentials(HttpServletRequest req) {
		final String authorization = req.getHeader("Authorization");
		if (authorization != null && authorization.startsWith("Basic")) {
			// Authorization: Basic base64credentials
			String base64Credentials = authorization.substring("Basic".length()).trim();
			String credentials = new String(Base64.getDecoder().decode(base64Credentials), Charset.forName("UTF-8"));

			// credentials = username:password
			return credentials.split(":", 2);
		} else
			return null;
	}

	public void setAuthenticationChain(AuthenticationChain authenticationChain) {
		this.authenticationChain = authenticationChain;
	}

	public void destroy() {
		log.info("Stopping the session timeout watchdog");
		timeoutWatchDog.finish();

		for (Session session : getSessions()) {
			try {
				SessionManager.get().kill(session.getSid());
			} catch (Throwable t) {
			}
		}
		clear();

		if (timeoutWatchDog.isAlive()) {
			try {
				timeoutWatchDog.interrupt();
			} catch (Throwable t) {

			}
			log.info("Session timeout watch dog killed");
		}
	}

	/**
	 * Each minute iterates over the sessions killing the expired ones
	 * 
	 * @author Marco Meschieri - LogicalDOC
	 * @since 7.5.3
	 */
	class SessionTimeoutWatchDog extends Thread {
		boolean active = true;

		private SessionTimeoutWatchDog() {
			setDaemon(true);
			setName("SessionTimeoutWatchDog");
		}

		@Override
		public void run() {
			while (active) {
				try {
					Thread.sleep(1000 * 60L);
				} catch (InterruptedException e) {

				}
				for (Session session : SessionManager.this.getSessions()) {
					if (session.isOpen() && session.isTimedOut()) {
						session.setExpired();
						storeSession(session);
					}
				}
			}
		}

		public void finish() {
			this.active = false;
		}

		public boolean isActive() {
			return active;
		}
	}

	public SessionDAO getSessionDao() {
		return sessionDao;
	}

	public void setSessionDao(SessionDAO sessionDao) {
		this.sessionDao = sessionDao;
	}
}