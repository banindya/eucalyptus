/*************************************************************************
 * (c) Copyright 2017 Hewlett Packard Enterprise Development Company LP
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
 ************************************************************************/
package com.eucalyptus.portal.awsusage;

import com.amazonaws.services.simpleworkflow.model.WorkflowExecutionAlreadyStartedException;
import com.eucalyptus.portal.WorkflowClients;
import com.eucalyptus.portal.workflow.AwsUsageHourlyAggregateWorkflowClientExternal;
import com.eucalyptus.simpleworkflow.common.client.WorkflowStarter;
import org.apache.log4j.Logger;

public class AwsUsageHourlyWorkflowStarter implements WorkflowStarter {
  private static Logger LOG     =
          Logger.getLogger(  AwsUsageHourlyWorkflowStarter.class );

  public static final String BILLING_AWS_USAGE_HOURLY_AGGREGATE_WORKFLOW_ID =
          "billing-aws-usage-hourly-aggregate-workflow-01";

  @Override
  public void start() {
    try{
      final AwsUsageHourlyAggregateWorkflowClientExternal workflow =
              WorkflowClients.getAwsUsageHourlyAggregateWorkflow(BILLING_AWS_USAGE_HOURLY_AGGREGATE_WORKFLOW_ID);
      workflow.aggregateHourly();
    }catch(final WorkflowExecutionAlreadyStartedException ex ) {
      ;
    }catch(final Exception ex) {
      LOG.error("Failed to start workflow for aggregating hourly AWS usage report", ex);
    }
  }

  @Override
  public String name() {
    return "AWS_USAGE_HOURLY_WORKFLOW";
  }
}
