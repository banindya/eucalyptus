/*******************************************************************************
 *Copyright (c) 2009  Eucalyptus Systems, Inc.
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, only version 3 of the License.
 * 
 * 
 *  This file is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 * 
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Please contact Eucalyptus Systems, Inc., 130 Castilian
 *  Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 *  if you need additional information or have any questions.
 * 
 *  This file may incorporate work covered under the following copyright and
 *  permission notice:
 * 
 *    Software License Agreement (BSD License)
 * 
 *    Copyright (c) 2008, Regents of the University of California
 *    All rights reserved.
 * 
 *    Redistribution and use of this software in source and binary forms, with
 *    or without modification, are permitted provided that the following
 *    conditions are met:
 * 
 *      Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 * 
 *      Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 * 
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *    IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *    TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *    PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 *    OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *    PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *    LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *    NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 *    THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 *    LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 *    SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *    IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 *    BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 *    THE REGENTS’ DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 *    OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 *    WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 *    ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************/
/*
 *
 * Author: Neil Soman neil@eucalyptus.com
 */

package edu.ucsb.eucalyptus.storage;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.eucalyptus.util.BaseDirectory;
import com.eucalyptus.util.EntityWrapper;
import com.eucalyptus.util.EucalyptusCloudException;
import com.eucalyptus.util.ExecutionException;
import com.eucalyptus.util.StorageProperties;

import edu.ucsb.eucalyptus.cloud.entities.CHAPUserInfo;
import edu.ucsb.eucalyptus.cloud.entities.EquallogicVolumeInfo;
import edu.ucsb.eucalyptus.ic.StorageController;
import edu.ucsb.eucalyptus.util.SystemUtil;

public class EquallogicProvider implements SANProvider {
	private static Logger LOG = Logger.getLogger(EquallogicProvider.class);
	private String host;
	private String username;
	private String password;
	private final String TARGET_USERNAME = "eucalyptus"; 
	private boolean enabled;
	private SessionManager sessionManager;

	private static final Pattern VOLUME_CREATE_PATTERN = Pattern.compile(".*iSCSI target name is (.*)\r");
	private static final Pattern VOLUME_DELETE_PATTERN = Pattern.compile(".*Volume deletion succeeded.");
	private static final Pattern USER_CREATE_PATTERN = Pattern.compile(".*Password is (.*)\r");
	private static final Pattern SNAPSHOT_CREATE_PATTERN = Pattern.compile(".*Snapshot name is (.*)\r");
	private static final Pattern SNAPSHOT_TARGET_NAME_PATTERN = Pattern.compile(".*iSCSI Name: (.*)\r");
	private static final Pattern SNAPSHOT_DELETE_PATTERN = Pattern.compile("Snapshot deletion succeeded.");
	private static final Pattern USER_DELETE_PATTERN = Pattern.compile("CHAP user deletion succeeded.");
	private static final Pattern USER_SHOW_PATTERN = Pattern.compile(".*Password: (.*)\r");

	private final long TASK_TIMEOUT = 5 * 60 * 1000;

	public EquallogicProvider() {}

	public void configure() {
		this.host = StorageProperties.SAN_HOST;
		this.username = StorageProperties.SAN_USERNAME;
		this.password = StorageProperties.SAN_PASSWORD;
		if(sessionManager != null)
			try {
				sessionManager.update(host, username, password);
			} catch (EucalyptusCloudException e) {
				LOG.error(e, e);
			}
			else {
				sessionManager = new SessionManager(host, username, password);
				sessionManager.start();
			}
	}

	public EquallogicProvider(String host, String username, String password) {
		this.host = host;
		this.username = username;
		this.password = password;
		sessionManager = new SessionManager(host, username, password);
		sessionManager.start();
	}

	public void checkConnection() {
		try {
			sessionManager.checkConnection();
		} catch (EucalyptusCloudException e) {
			enabled = false;
			return;
		}
		addUser(TARGET_USERNAME);
	}

	public String createVolume(String volumeId, String snapshotId,
			boolean locallyCreated, String sourceVolume) {
		if(!enabled) {
			checkConnection();
			if(!enabled) {
				LOG.error("Unable to create user " + TARGET_USERNAME + " on target. Will not run command. ");
				return null;
			}
		}
		if(locallyCreated) {
			try {
				String returnValue = execCommand("stty hardwrap off\u001Avolume select " + sourceVolume + 
						" snapshot select " + snapshotId + " clone " + volumeId + "\u001A");
				String targetName = matchPattern(returnValue, VOLUME_CREATE_PATTERN);
				if(targetName != null) {
					returnValue = execCommand("volume select " + volumeId + " access create username " + TARGET_USERNAME + "\u001A");
					if(returnValue.length() == 0) {
						LOG.error("Unable to set access for volume: " + volumeId);
						return null;
					}
				}
				return targetName;
			} catch (EucalyptusCloudException e) {
				LOG.error(e);
				return null;
			}	
		} else {
			try {
				String returnValue = execCommand("stty hardwrap off\u001Avolume select " + snapshotId + 
						" offline\u001Avolume select " + snapshotId + " clone " + volumeId + "\u001A");
				String targetName = matchPattern(returnValue, VOLUME_CREATE_PATTERN);
				if(targetName != null) {
					returnValue = execCommand("volume select " + volumeId + " access create username " + TARGET_USERNAME + "\u001A");
					if(returnValue.length() == 0) {
						LOG.error("Unable to set access for volume: " + volumeId);
						return null;
					}
				}
				return targetName;
			} catch (EucalyptusCloudException e) {
				LOG.error(e);
				return null;
			}	
		}
	}

	public String connectTarget(String iqn) throws EucalyptusCloudException {
		EntityWrapper<CHAPUserInfo> db = StorageController.getEntityWrapper();
		try {
			CHAPUserInfo userInfo = db.getUnique(new CHAPUserInfo(TARGET_USERNAME));
			String encryptedPassword = userInfo.getEncryptedPassword();
			db.commit();
			try {
				String deviceName = SystemUtil.run(new String[]{"sudo", "-E", BaseDirectory.LIB.toString() + File.separator + "connect_iscsitarget_sc.pl", 
						host + "," + iqn + "," + encryptedPassword});
				return deviceName;
			} catch (ExecutionException e) {
				throw new EucalyptusCloudException("Unable to connect to storage target");
			}				
		} catch(EucalyptusCloudException ex) {
			db.rollback();
			throw new EucalyptusCloudException("Unable to get CHAP password");
		}
	}

	public String getVolumeProperty(String volumeId) {
		EntityWrapper<EquallogicVolumeInfo> db = StorageController.getEntityWrapper();
		try {
			EquallogicVolumeInfo volumeInfo = db.getUnique(new EquallogicVolumeInfo(volumeId));
			EntityWrapper<CHAPUserInfo> dbUser = db.recast(CHAPUserInfo.class);
			CHAPUserInfo userInfo = dbUser.getUnique(new CHAPUserInfo("eucalyptus"));
			String property = host + "," + volumeInfo.getIqn() + "," + BlockStorageUtil.encryptNodeTargetPassword(BlockStorageUtil.decryptSCTargetPassword(userInfo.getEncryptedPassword()));
			db.commit();
			return property;
		} catch(EucalyptusCloudException ex) {
			LOG.error(ex);
			db.rollback();
			return null;
		}
	}

	public String execCommand(String command) throws EucalyptusCloudException {
		SANTask task = new SANTask(command);
		try {
			sessionManager.addTask(task);
			if(task.getValue() == null) {
				synchronized (task) {
					task.wait(TASK_TIMEOUT);
				}
			}
			if(task.getValue() == null) {
				LOG.error("Unable to execute command: " + task.getCommand());
				return "";
			} else {
				return task.getValue();
			}					

		} catch (InterruptedException e) {
			LOG.error(e);
			return "";
		}
	}

	private String matchPattern(String input,
			Pattern pattern) {
		Matcher m = pattern.matcher(input);
		if(m.find()) 
			return m.group(1);
		else
			return null;			
	}

	public String createVolume(String volumeName, int size) {
		if(!enabled) {
			checkConnection();
			if(!enabled) {
				LOG.error("Not enabled. Will not run command. ");
				return null;
			}
		}
		try {
			String returnValue = execCommand("stty hardwrap off\u001Avolume create " + volumeName + " " + (size * StorageProperties.KB) + "\u001A");
			String targetName = matchPattern(returnValue, VOLUME_CREATE_PATTERN);
			if(targetName != null) {
				returnValue = execCommand("volume select " + volumeName + " access create username " + TARGET_USERNAME + "\u001A");
				if(returnValue.length() == 0) {
					LOG.error("Unable to set access for volume: " + volumeName);
					return null;
				}
			}
			return targetName;
		} catch (EucalyptusCloudException e) {
			LOG.error(e);
			return null;
		}
	}

	public boolean deleteVolume(String volumeName) {
		if(!enabled) {
			checkConnection();
			if(!enabled) {
				LOG.error("Not enabled. Will not run command. ");
				return false;
			}
		}
		try {
			String returnValue = execCommand("stty hardwrap off\u001Avolume select " + volumeName + " offline\u001Avolume delete " + volumeName + "\u001A");
			if(returnValue.split(VOLUME_DELETE_PATTERN.toString()).length > 1)
				return true;
			else
				return false;
		} catch(EucalyptusCloudException e) {
			LOG.error(e);
			return false;
		}
	}

	public String createSnapshot(String volumeId, String snapshotId) {
		if(!enabled) {
			checkConnection();
			if(!enabled) {
				LOG.error("Not enabled. Will not run command. ");
				return null;
			}
		}
		try {
			String returnValue = execCommand("stty hardwrap off\u001Avolume select " + volumeId + " snapshot create-now\u001A");
			String snapName = matchPattern(returnValue, SNAPSHOT_CREATE_PATTERN);
			if(snapName != null) {
				returnValue = execCommand("volume select " + volumeId + " snapshot rename " + snapName + " " + snapshotId + "\u001A");
				returnValue = execCommand("volume select " + volumeId + " snapshot select " + snapshotId + " online\u001A");
				returnValue = execCommand("stty hardwrap off\u001Avolume select " + volumeId + " snapshot select " + snapshotId + " show\u001A");
				return matchPattern(returnValue, SNAPSHOT_TARGET_NAME_PATTERN);
			}
			return null;
		} catch (EucalyptusCloudException e) {
			LOG.error(e);
			return null;
		}
	}

	public boolean deleteSnapshot(String volumeId, String snapshotId, boolean locallyCreated) {
		if(!enabled) {
			checkConnection();
			if(!enabled) {
				LOG.error("Not enabled. Will not run command. ");
				return false;
			}
		}
		if(locallyCreated) {
			try {				
				String returnValue = execCommand("stty hardwrap off\u001Avolume select " + volumeId + " snapshot select " + snapshotId + " offline\u001Avolume select " + volumeId + " snapshot delete " + snapshotId + "\u001A");
				if(returnValue.split(SNAPSHOT_DELETE_PATTERN.toString()).length > 1)
					return true;
				else
					return false;
			} catch(EucalyptusCloudException e) {
				LOG.error(e);
				return false;
			}
		} else {
			try {
				String returnValue = execCommand("stty hardwrap off\u001Avolume select " + snapshotId + " offline\u001Avolume delete " + snapshotId + "\u001A");
				if(returnValue.split(VOLUME_DELETE_PATTERN.toString()).length > 1)
					return true;
				else
					return false;
			} catch(EucalyptusCloudException e) {
				LOG.error(e);
				return false;
			}
		}
	}

	public void deleteUser(String userName) throws EucalyptusCloudException {
		if(!enabled) {
			checkConnection();
			if(!enabled) {
				LOG.error("Not enabled. Will not run command. ");
				throw new EucalyptusCloudException("Not enabled. Will not run command.");
			}
		}
		EntityWrapper<CHAPUserInfo> db = StorageController.getEntityWrapper();
		try {
			CHAPUserInfo userInfo = db.getUnique(new CHAPUserInfo(userName));
			String returnValue = execCommand("stty hardwrap off\u001Achapuser delete " + userName + "\u001A");
			if(matchPattern(returnValue, USER_DELETE_PATTERN) != null) {
				db.delete(userInfo);
			}
		} catch(EucalyptusCloudException ex) {
			throw new EucalyptusCloudException("Unable to find user: " + userName);
		} finally {
			db.commit();
		}

	}

	public void addUser(String userName){
		EntityWrapper<CHAPUserInfo> db = StorageController.getEntityWrapper();
		try {
			CHAPUserInfo userInfo = db.getUnique(new CHAPUserInfo(userName));
			db.commit();
			enabled = true;
		} catch(EucalyptusCloudException ex) {
			db.rollback();
			try {
				String returnValue = execCommand("stty hardwrap off\u001Achapuser create " + userName + "\u001A");
				String password = matchPattern(returnValue, USER_CREATE_PATTERN);
				if(password != null) {
					db = StorageController.getEntityWrapper();
					CHAPUserInfo userInfo = new CHAPUserInfo(userName, BlockStorageUtil.encryptSCTargetPassword(password));
					db.add(userInfo);
					db.commit();
					enabled = true;
				} else {
					returnValue = execCommand("stty hardwrap off\u001Achapuser show " + userName + "\u001A");
					password = matchPattern(returnValue, USER_SHOW_PATTERN);
					if(password != null) {
						db = StorageController.getEntityWrapper();
						CHAPUserInfo userInfo = new CHAPUserInfo(userName, BlockStorageUtil.encryptSCTargetPassword(password));
						db.add(userInfo);
						db.commit();
						enabled = true;
					}
				}
			} catch (EucalyptusCloudException e) {
				LOG.error(e);
			}
		}
	}

	public void disconnectTarget(String iqn) throws EucalyptusCloudException {
		EntityWrapper<CHAPUserInfo> db = StorageController.getEntityWrapper();
		try {
			CHAPUserInfo userInfo = db.getUnique(new CHAPUserInfo(TARGET_USERNAME));
			String encryptedPassword = userInfo.getEncryptedPassword();
			db.commit();
			try {
				String returnValue = SystemUtil.run(new String[]{"sudo", "-E", BaseDirectory.LIB.toString() + File.separator + "disconnect_iscsitarget_sc.pl", 
						host + "," + iqn + "," + encryptedPassword});
				if(returnValue.length() == 0) {
					throw new EucalyptusCloudException("Unable to disconnect target");
				}
			} catch (ExecutionException e) {
				throw new EucalyptusCloudException("Unable to connect to storage target");
			}				
		} catch(EucalyptusCloudException ex) {
			db.rollback();
			throw new EucalyptusCloudException(ex);
		}
	}
}

