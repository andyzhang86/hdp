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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEntity;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEvent;

import org.apache.tez.common.counters.TezCounters;
import org.apache.tez.dag.api.DagTypeConverters;
import org.apache.tez.dag.api.oldrecords.TaskState;
import org.apache.tez.dag.history.HistoryEvent;
import org.apache.tez.dag.history.HistoryEventType;
import org.apache.tez.dag.history.ats.EntityTypes;
import org.apache.tez.dag.history.utils.ATSConstants;
import org.apache.tez.dag.history.utils.DAGUtils;
import org.apache.tez.dag.records.TezTaskAttemptID;
import org.apache.tez.dag.records.TezTaskID;
import org.apache.tez.dag.recovery.records.RecoveryProtos.TaskFinishedProto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TaskFinishedEvent implements HistoryEvent {

  private static final Log LOG = LogFactory.getLog(TaskFinishedEvent.class);

  private TezTaskID taskID;
  private String vertexName;
  private long startTime;
  private long finishTime;
  private TaskState state;
  private TezCounters tezCounters;
  private TezTaskAttemptID successfulAttemptID;

  public TaskFinishedEvent(TezTaskID taskID,
      String vertexName, long startTime, long finishTime,
      TezTaskAttemptID successfulAttemptID,
      TaskState state, TezCounters counters) {
    this.vertexName = vertexName;
    this.taskID = taskID;
    this.startTime = startTime;
    this.finishTime = finishTime;
    this.state = state;
    this.tezCounters = counters;
  }

  public TaskFinishedEvent() {
  }

  @Override
  public HistoryEventType getEventType() {
    return HistoryEventType.TASK_FINISHED;
  }

  @Override
  public boolean isRecoveryEvent() {
    return true;
  }

  @Override
  public boolean isHistoryEvent() {
    return true;
  }

  public TaskFinishedProto toProto() {
    TaskFinishedProto.Builder builder = TaskFinishedProto.newBuilder();
    builder.setTaskId(taskID.toString())
        .setState(state.ordinal())
        .setFinishTime(finishTime);
    if (tezCounters != null) {
      builder.setCounters(DagTypeConverters.convertTezCountersToProto(tezCounters));
    }
    if (successfulAttemptID != null) {
      builder.setSuccessfulTaskAttemptId(successfulAttemptID.toString());
    }
    return builder.build();
  }

  public void fromProto(TaskFinishedProto proto) {
    this.taskID = TezTaskID.fromString(proto.getTaskId());
    this.finishTime = proto.getFinishTime();
    this.state = TaskState.values()[proto.getState()];
    if (proto.hasCounters()) {
      this.tezCounters = DagTypeConverters.convertTezCountersFromProto(
          proto.getCounters());
    }
    if (proto.hasSuccessfulTaskAttemptId()) {
      this.successfulAttemptID =
          TezTaskAttemptID.fromString(proto.getSuccessfulTaskAttemptId());
    }
  }

  @Override
  public void toProtoStream(OutputStream outputStream) throws IOException {
    toProto().writeDelimitedTo(outputStream);
  }

  @Override
  public void fromProtoStream(InputStream inputStream) throws IOException {
    TaskFinishedProto proto = TaskFinishedProto.parseDelimitedFrom(inputStream);
    fromProto(proto);
  }

  @Override
  public TimelineEntity convertToTimelineEntity() {
    TimelineEntity atsEntity = new TimelineEntity();
    atsEntity.setEntityId(taskID.toString());
    atsEntity.setEntityType(EntityTypes.TEZ_TASK_ID.name());

    atsEntity.addPrimaryFilter(EntityTypes.TEZ_DAG_ID.name(),
        taskID.getVertexID().getDAGId().toString());
    atsEntity.addPrimaryFilter(EntityTypes.TEZ_VERTEX_ID.name(),
        taskID.getVertexID().toString());

    TimelineEvent finishEvt = new TimelineEvent();
    finishEvt.setEventType(HistoryEventType.TASK_FINISHED.name());
    finishEvt.setTimestamp(finishTime);
    atsEntity.addEvent(finishEvt);

    atsEntity.addOtherInfo(ATSConstants.FINISH_TIME, finishTime);
    atsEntity.addOtherInfo(ATSConstants.TIME_TAKEN, (finishTime - startTime));
    atsEntity.addOtherInfo(ATSConstants.STATUS, state.name());

    atsEntity.addOtherInfo(ATSConstants.COUNTERS,
        DAGUtils.convertCountersToATSMap(tezCounters));

    return atsEntity;
  }

  @Override
  public String toString() {
    return "vertexName=" + vertexName
        + ", taskId=" + taskID
        + ", startTime=" + startTime
        + ", finishTime=" + finishTime
        + ", timeTaken=" + (finishTime - startTime)
        + ", status=" + state.name()
        + ", successfulAttemptID=" + (successfulAttemptID == null ? "null" :
            successfulAttemptID.toString())
        + ", counters=" + ( tezCounters == null ? "null" :
          tezCounters.toString()
            .replaceAll("\\n", ", ").replaceAll("\\s+", " "));
  }

  public TezTaskID getTaskID() {
    return taskID;
  }

  public TaskState getState() {
    return state;
  }

  public long getFinishTime() {
    return finishTime;
  }

  public TezCounters getTezCounters() {
    return tezCounters;
  }

  public TezTaskAttemptID getSuccessfulAttemptID() {
    return successfulAttemptID;
  }
}
