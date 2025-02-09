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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEntity;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEvent;
import org.apache.tez.common.counters.TezCounters;
import org.apache.tez.dag.api.DagTypeConverters;
import org.apache.tez.dag.app.dag.DAGState;
import org.apache.tez.dag.history.HistoryEvent;
import org.apache.tez.dag.history.HistoryEventType;
import org.apache.tez.dag.history.SummaryEvent;
import org.apache.tez.dag.history.ats.EntityTypes;
import org.apache.tez.dag.history.utils.ATSConstants;
import org.apache.tez.dag.history.utils.DAGUtils;
import org.apache.tez.dag.records.TezDAGID;
import org.apache.tez.dag.recovery.records.RecoveryProtos;
import org.apache.tez.dag.recovery.records.RecoveryProtos.DAGFinishedProto;
import org.apache.tez.dag.recovery.records.RecoveryProtos.SummaryEventProto;

import com.google.common.primitives.Ints;
import com.google.protobuf.ByteString;

public class DAGFinishedEvent implements HistoryEvent, SummaryEvent {

  private static final Log LOG = LogFactory.getLog(DAGFinishedEvent.class);

  private TezDAGID dagID;
  private long startTime;
  private long finishTime;
  private DAGState state;
  private String diagnostics;
  private TezCounters tezCounters;
  private String user;
  private String dagName;

  public DAGFinishedEvent() {
  }

  public DAGFinishedEvent(TezDAGID dagId, long startTime,
      long finishTime, DAGState state,
      String diagnostics, TezCounters counters,
      String user, String dagName) {
    this.dagID = dagId;
    this.startTime = startTime;
    this.finishTime = finishTime;
    this.state = state;
    this.diagnostics = diagnostics;
    this.tezCounters = counters;
    this.user = user;
    this.dagName = dagName;
  }

  @Override
  public HistoryEventType getEventType() {
    return HistoryEventType.DAG_FINISHED;
  }

  @Override
  public boolean isRecoveryEvent() {
    return true;
  }

  @Override
  public boolean isHistoryEvent() {
    return true;
  }

  public DAGFinishedProto toProto() {
    DAGFinishedProto.Builder builder = DAGFinishedProto.newBuilder();

    builder.setDagId(dagID.toString())
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

  public void fromProto(DAGFinishedProto proto) {
    this.dagID = TezDAGID.fromString(proto.getDagId());
    this.finishTime = proto.getFinishTime();
    this.state = DAGState.values()[proto.getState()];
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
    DAGFinishedProto proto = DAGFinishedProto.parseDelimitedFrom(inputStream);
    fromProto(proto);
  }

  @Override
  public TimelineEntity convertToTimelineEntity() {
    TimelineEntity atsEntity = new TimelineEntity();
    atsEntity.setEntityId(dagID.toString());
    atsEntity.setEntityType(EntityTypes.TEZ_DAG_ID.name());

    TimelineEvent finishEvt = new TimelineEvent();
    finishEvt.setEventType(HistoryEventType.DAG_FINISHED.name());
    finishEvt.setTimestamp(finishTime);
    atsEntity.addEvent(finishEvt);

    atsEntity.addPrimaryFilter(ATSConstants.USER, user);
    atsEntity.addPrimaryFilter(ATSConstants.DAG_NAME, dagName);

    atsEntity.addOtherInfo(ATSConstants.START_TIME, startTime);
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
    return "dagId=" + dagID
        + ", startTime=" + startTime
        + ", finishTime=" + finishTime
        + ", timeTaken=" + (finishTime - startTime)
        + ", status=" + state.name()
        + ", diagnostics=" + diagnostics
        + ", counters=" + ((tezCounters == null) ? "null" :
          (tezCounters.toString()
            .replaceAll("\\n", ", ").replaceAll("\\s+", " ")));
  }

  @Override
  public void toSummaryProtoStream(OutputStream outputStream) throws IOException {
    SummaryEventProto.Builder builder = RecoveryProtos.SummaryEventProto.newBuilder()
        .setDagId(dagID.toString())
        .setTimestamp(finishTime)
        .setEventType(getEventType().ordinal())
        .setEventPayload(ByteString.copyFrom(Ints.toByteArray(state.ordinal())));
    builder.build().writeDelimitedTo(outputStream);
  }

  @Override
  public void fromSummaryProtoStream(SummaryEventProto proto) throws IOException {
    this.dagID = TezDAGID.fromString(proto.getDagId());
    this.finishTime = proto.getTimestamp();
    this.state = DAGState.values()[
        Ints.fromByteArray(proto.getEventPayload().toByteArray())];
  }

  @Override
  public boolean writeToRecoveryImmediately() {
    return true;
  }

  public long getFinishTime() {
    return this.finishTime;
  }

  public DAGState getState() {
    return state;
  }

  public TezDAGID getDagID() {
    return dagID;
  }

  public long getStartTime() {
    return startTime;
  }

  public String getDiagnostics() {
    return diagnostics;
  }

  public TezCounters getTezCounters() {
    return tezCounters;
  }

}
