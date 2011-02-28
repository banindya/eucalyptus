/*******************************************************************************
 * Copyright (c) 2009  Eucalyptus Systems, Inc.
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
 *******************************************************************************
 * @author chris grzegorczyk <grze@eucalyptus.com>
 */

package com.eucalyptus.bootstrap;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.log4j.Logger;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import com.eucalyptus.component.Components;
import com.eucalyptus.component.auth.SystemCredentialProvider;
import com.eucalyptus.component.id.Eucalyptus;
import com.eucalyptus.crypto.Crypto;
import com.eucalyptus.crypto.Hmacs;
import com.eucalyptus.empyrean.Empyrean;

@Provides( Empyrean.class )
@RunDuring( Bootstrap.Stage.RemoteConfiguration )
public class MembershipBootstrapper extends Bootstrapper {
  private static Logger LOG = Logger.getLogger( MembershipBootstrapper.class );
  private JChannel      membershipChannel;
  private String        membershipGroupName;
  
  @Override
  public boolean load( ) throws Exception {
    try {
      this.membershipGroupName = "Eucalyptus-" + Hmacs.generateSystemSignature( ).hashCode( );
      this.membershipChannel = MembershipManager.buildChannel( );
      return true;
    } catch ( Exception ex ) {
      LOG.fatal( ex, ex );
      BootstrapException.throwFatal( "Failed to construct membership channel because of " + ex.getMessage( ), ex );
      return false;
    }
  }
  
  @Override
  public boolean start( ) throws Exception {
    try {
      final boolean[] done = { false };
      final ReentrantLock lock = new ReentrantLock( );
      final Condition isReady = lock.newCondition( );
      this.membershipChannel.setReceiver( new ReceiverAdapter( ) {
        public void viewAccepted( View new_view ) {
          lock.lock( );
          try {
            if ( System.getProperty( "euca.cloud.disable" ) != null ) {
              done[0] = true;
              isReady.signalAll( );
            }
            LOG.info( "view: " + new_view );
          } finally {
            lock.unlock( );
          }
        }
        
        public void receive( Message msg ) {
          LOG.info( msg.getObject( ) + " [" + msg.getSrc( ) + "]" );
        }
      } );
      lock.lock( );
      try {
        this.membershipChannel.connect( this.membershipGroupName );
        LOG.info( "Started membership channel " + this.membershipGroupName );
        if ( System.getProperty( "euca.cloud.disable" ) != null ) {
          LOG.warn( "Blocking the bootstrap thread for testing." );
          if( !done[0] ) {
            isReady.await( );
          }
        }
      } finally {
        lock.unlock( );
      }
      return true;
    } catch ( Exception ex ) {
      LOG.fatal( ex, ex );
      BootstrapException.throwFatal( "Failed to connect membership channel because of " + ex.getMessage( ), ex );
      return false;
    }
  }
  
  @Override
  public boolean enable( ) throws Exception {
    return false;
  }
  
  @Override
  public boolean stop( ) throws Exception {
    return false;
  }
  
  @Override
  public void destroy( ) throws Exception {}
  
  @Override
  public boolean disable( ) throws Exception {
    return false;
  }
  
  @Override
  public boolean check( ) throws Exception {
    return false;
  }
  
}
