/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tez.dag.history.events;

import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEntity;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEvent;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.tez.dag.history.HistoryEvent;
import org.apache.tez.dag.history.HistoryEventType;
import org.apache.tez.dag.history.ats.EntityTypes;
import org.apache.tez.dag.history.utils.ATSConstants;
import org.apache.tez.dag.records.TezTaskAttemptID;
import org.apache.tez.dag.recovery.records.RecoveryProtos.TaskAttemptStartedProto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TaskAttemptStartedEvent implements HistoryEvent {

  private TezTaskAttemptID taskAttemptId;
  private String inProgressLogsUrl;
  private String completedLogsUrl;
  private String vertexName;
  private long startTime;
  private ContainerId containerId;
  private NodeId nodeId;

  public TaskAttemptStartedEvent(TezTaskAttemptID taId,
      String vertexName, long startTime,
      ContainerId containerId, NodeId nodeId,
      String inProgressLogsUrl, String completedLogsUrl) {
    this.taskAttemptId = taId;
    this.vertexName = vertexName;
    this.startTime = startTime;
    this.containerId = containerId;
    this.nodeId = nodeId;
    this.inProgressLogsUrl = inProgressLogsUrl;
    this.completedLogsUrl = completedLogsUrl;
  }

  public TaskAttemptStartedEvent() {
  }

  @Override
  public HistoryEventType getEventType() {
    return HistoryEventType.TASK_ATTEMPT_STARTED;
  }

  @Override
  public boolean isRecoveryEvent() {
    return true;
  }

  @Override
  public boolean isHistoryEvent() {
    return true;
  }

  public TaskAttemptStartedProto toProto() {
    return TaskAttemptStartedProto.newBuilder()
        .setTaskAttemptId(taskAttemptId.toString())
        .setStartTime(startTime)
        .setContainerId(containerId.toString())
        .setNodeId(nodeId.toString())
        .build();
  }

  public void fromProto(TaskAttemptStartedProto proto) {
    this.taskAttemptId = TezTaskAttemptID.fromString(proto.getTaskAttemptId());
    this.startTime = proto.getStartTime();
    this.containerId = ConverterUtils.toContainerId(proto.getContainerId());
    this.nodeId = ConverterUtils.toNodeId(proto.getNodeId());
  }

  @Override
  public void toProtoStream(OutputStream outputStream) throws IOException {
    toProto().writeDelimitedTo(outputStream);
  }

  @Override
  public void fromProtoStream(InputStream inputStream) throws IOException {
    TaskAttemptStartedProto proto = TaskAttemptStartedProto.parseDelimitedFrom(inputStream);
    fromProto(proto);
  }

  @Override
  public TimelineEntity convertToTimelineEntity() {
    TimelineEntity atsEntity = new TimelineEntity();
    atsEntity.setEntityId(taskAttemptId.toString());
    atsEntity.setEntityType(EntityTypes.TEZ_TASK_ATTEMPT_ID.name());

    atsEntity.setStartTime(startTime);

    atsEntity.addRelatedEntity(ATSConstants.NODE_ID, nodeId.toString());
    atsEntity.addRelatedEntity(ATSConstants.CONTAINER_ID, containerId.toString());
    atsEntity.addRelatedEntity(EntityTypes.TEZ_TASK_ID.name(),
        taskAttemptId.getTaskID().toString());

    atsEntity.addPrimaryFilter(EntityTypes.TEZ_DAG_ID.name(),
        taskAttemptId.getTaskID().getVertexID().getDAGId().toString());
    atsEntity.addPrimaryFilter(EntityTypes.TEZ_VERTEX_ID.name(),
        taskAttemptId.getTaskID().getVertexID().toString());
    atsEntity.addPrimaryFilter(EntityTypes.TEZ_TASK_ID.name(),
        taskAttemptId.getTaskID().toString());

    TimelineEvent startEvt = new TimelineEvent();
    startEvt.setEventType(HistoryEventType.TASK_ATTEMPT_STARTED.name());
    startEvt.setTimestamp(startTime);
    atsEntity.addEvent(startEvt);

    atsEntity.addOtherInfo(ATSConstants.START_TIME, startTime);
    atsEntity.addOtherInfo(ATSConstants.IN_PROGRESS_LOGS_URL, inProgressLogsUrl);
    atsEntity.addOtherInfo(ATSConstants.COMPLETED_LOGS_URL, completedLogsUrl);

    return atsEntity;
  }

  @Override
  public String toString() {
    return "vertexName=" + vertexName
        + ", taskAttemptId=" + taskAttemptId
        + ", startTime=" + startTime
        + ", containerId=" + containerId
        + ", nodeId=" + nodeId
        + ", inProgressLogs=" + inProgressLogsUrl
        + ", completedLogs=" + completedLogsUrl;
  }

  public TezTaskAttemptID getTaskAttemptID() {
    return this.taskAttemptId;
  }

  public long getStartTime() {
    return startTime;
  }

  public ContainerId getContainerId() {
    return containerId;
  }

  public NodeId getNodeId() {
    return nodeId;
  }
}
