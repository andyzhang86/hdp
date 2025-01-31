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
import org.apache.tez.dag.api.oldrecords.TaskAttemptState;
import org.apache.tez.dag.history.HistoryEvent;
import org.apache.tez.dag.history.HistoryEventType;
import org.apache.tez.dag.history.ats.EntityTypes;
import org.apache.tez.dag.history.utils.ATSConstants;
import org.apache.tez.dag.history.utils.DAGUtils;
import org.apache.tez.dag.records.TezTaskAttemptID;
import org.apache.tez.dag.recovery.records.RecoveryProtos.TaskAttemptFinishedProto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TaskAttemptFinishedEvent implements HistoryEvent {

  private static final Log LOG = LogFactory.getLog(TaskAttemptFinishedEvent.class);

  private TezTaskAttemptID taskAttemptId;
  private String vertexName;
  private long startTime;
  private long finishTime;
  private TaskAttemptState state;
  private String diagnostics;
  private TezCounters tezCounters;

  public TaskAttemptFinishedEvent(TezTaskAttemptID taId,
      String vertexName,
      long startTime,
      long finishTime,
      TaskAttemptState state,
      String diagnostics,
      TezCounters counters) {
    this.taskAttemptId = taId;
    this.vertexName = vertexName;
    this.startTime = startTime;
    this.finishTime = finishTime;
    this.state = state;
    this.diagnostics = diagnostics;
    tezCounters = counters;
  }

  public TaskAttemptFinishedEvent() {
  }

  @Override
  public HistoryEventType getEventType() {
    return HistoryEventType.TASK_ATTEMPT_FINISHED;
  }

  @Override
  public boolean isRecoveryEvent() {
    return true;
  }

  @Override
  public boolean isHistoryEvent() {
    return true;
  }

  public TaskAttemptFinishedProto toProto() {
    TaskAttemptFinishedProto.Builder builder =
        TaskAttemptFinishedProto.newBuilder();
    builder.setTaskAttemptId(taskAttemptId.toString())
        .setState(state.ordinal())
        .setFinishTime(finishTime);
    if (diagnostics != null) {
      builder.setDiagnostics(diagnostics);
    }
    if (tezCounters != null) {
      builder.setCounters(DagTypeConverters.convertTezCountersToProto(tezCounters));
    }
    return builder.build();
  }

  public void fromProto(TaskAttemptFinishedProto proto) {
    this.taskAttemptId = TezTaskAttemptID.fromString(proto.getTaskAttemptId());
    this.finishTime = proto.getFinishTime();
    this.state = TaskAttemptState.values()[proto.getState()];
    if (proto.hasDiagnostics()) {
      this.diagnostics = proto.getDiagnostics();
    }
    if (proto.hasCounters()) {
      this.tezCounters = DagTypeConverters.convertTezCountersFromProto(
        proto.getCounters());
    }
  }

  @Override
  public void toProtoStream(OutputStream outputStream) throws IOException {
    toProto().writeDelimitedTo(outputStream);
  }

  @Override
  public void fromProtoStream(InputStream inputStream) throws IOException {
    TaskAttemptFinishedProto proto =
        TaskAttemptFinishedProto.parseDelimitedFrom(inputStream);
    fromProto(proto);
  }

  @Override
  public TimelineEntity convertToTimelineEntity() {
    TimelineEntity atsEntity = new TimelineEntity();
    atsEntity.setEntityId(taskAttemptId.toString());
    atsEntity.setEntityType(EntityTypes.TEZ_TASK_ATTEMPT_ID.name());

    atsEntity.addPrimaryFilter(EntityTypes.TEZ_DAG_ID.name(),
        taskAttemptId.getTaskID().getVertexID().getDAGId().toString());
    atsEntity.addPrimaryFilter(EntityTypes.TEZ_VERTEX_ID.name(),
        taskAttemptId.getTaskID().getVertexID().toString());
    atsEntity.addPrimaryFilter(EntityTypes.TEZ_TASK_ID.name(),
        taskAttemptId.getTaskID().toString());

    TimelineEvent finishEvt = new TimelineEvent();
    finishEvt.setEventType(HistoryEventType.TASK_ATTEMPT_FINISHED.name());
    finishEvt.setTimestamp(finishTime);
    atsEntity.addEvent(finishEvt);

    atsEntity.addOtherInfo(ATSConstants.FINISH_TIME, finishTime);
    atsEntity.addOtherInfo(ATSConstants.TIME_TAKEN, (finishTime - startTime));
    atsEntity.addOtherInfo(ATSConstants.STATUS, state.name());
    atsEntity.addOtherInfo(ATSConstants.DIAGNOSTICS, diagnostics);

    atsEntity.addOtherInfo(ATSConstants.COUNTERS,
        DAGUtils.convertCountersToATSMap(tezCounters));

    return atsEntity;
  }

  @Override
  public String toString() {
    return "vertexName=" + vertexName
        + ", taskAttemptId=" + taskAttemptId
        + ", startTime=" + startTime
        + ", finishTime=" + finishTime
        + ", timeTaken=" + (finishTime - startTime)
        + ", status=" + state.name()
        + ", diagnostics=" + diagnostics
        + ", counters=" + (tezCounters == null ? "null" :
          tezCounters.toString()
            .replaceAll("\\n", ", ").replaceAll("\\s+", " "));
  }

  public TezTaskAttemptID getTaskAttemptID() {
    return taskAttemptId;
  }

  public TezCounters getCounters() {
    return tezCounters;
  }

  public String getDiagnostics() {
    return diagnostics;
  }

  public long getFinishTime() {
    return finishTime;
  }

  public TaskAttemptState getState() {
    return state;
  }

}
