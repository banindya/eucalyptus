/*************************************************************************
 * Copyright 2009-2012 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.blockstorage.tests;


import com.eucalyptus.auth.util.Hashes;
import com.eucalyptus.blockstorage.BlockStorageController;
import com.eucalyptus.blockstorage.msgs.CreateStorageSnapshotResponseType;
import com.eucalyptus.blockstorage.msgs.CreateStorageSnapshotType;
import com.eucalyptus.objectstorage.util.WalrusProperties;
import com.eucalyptus.util.EucalyptusCloudException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;


import java.util.Date;

@Ignore("Manual development test")
public class CreateSnapshotTest {

    static BlockStorageController blockStorage;

    @Test
    public void testCreateSnapshot() throws Exception {

        String userId = "admin";

        String volumeId = "vol-Xj-6F2zFUTOAYQxx";
        String snapshotId = "snap-" + Hashes.getRandom(10);

        CreateStorageSnapshotType createSnapshotRequest = new CreateStorageSnapshotType();

        createSnapshotRequest.setUserId(userId);
        createSnapshotRequest.setVolumeId(volumeId);
        createSnapshotRequest.setSnapshotId(snapshotId);
        CreateStorageSnapshotResponseType createSnapshotResponse = blockStorage.CreateStorageSnapshot(createSnapshotRequest);
        System.out.println(createSnapshotResponse);

        while(true);
    }

    @Test
    public void testSendDummy() throws Exception {
        HttpClient httpClient = new HttpClient();
        String addr = System.getProperty(WalrusProperties.URL_PROPERTY) + "/meh/ttt.wsl?gg=vol&hh=snap";

        HttpMethodBase method = new PutMethod(addr);
        method.setRequestHeader("Authorization", "Euca");
        method.setRequestHeader("Date", (new Date()).toString());
        method.setRequestHeader("Expect", "100-continue");

        httpClient.executeMethod(method);
        String responseString = method.getResponseBodyAsString();
        System.out.println(responseString);
        method.releaseConnection();
    }

    @Test
    public void testGetSnapshotInfo() throws Exception {
        HttpClient httpClient = new HttpClient();
        String addr = System.getProperty(WalrusProperties.URL_PROPERTY) + "/snapset-FuXLn1MUHJ66BkK0/snap-zVl2kZJmjhxnEg..";

        HttpMethodBase method = new GetMethod(addr);
        method.setRequestHeader("Authorization", "Euca");
        method.setRequestHeader("Date", (new Date()).toString());
        method.setRequestHeader("Expect", "100-continue");
        method.setRequestHeader("EucaOperation", "GetSnapshotInfo");
        httpClient.executeMethod(method);
        String responseString = method.getResponseBodyAsString();
        System.out.println(responseString);
        method.releaseConnection();         
    }

    @BeforeClass
    public static void setUp() {
        blockStorage = new BlockStorageController();
        try {
			BlockStorageController.configure();
		} catch (EucalyptusCloudException e) {
			e.printStackTrace();
		}
    }

}