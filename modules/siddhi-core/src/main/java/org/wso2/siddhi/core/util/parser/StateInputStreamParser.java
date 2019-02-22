/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.siddhi.core.util.parser;

import com.google.common.primitives.Ints;
import io.siddhi.core.config.SiddhiAppContext;
import io.siddhi.core.event.state.MetaStateEvent;
import io.siddhi.core.exception.OperationNotSupportedException;
import io.siddhi.core.executor.VariableExpressionExecutor;
import org.wso2.siddhi.core.query.input.ProcessStreamReceiver;
import org.wso2.siddhi.core.query.input.stream.single.EntryValveProcessor;
import org.wso2.siddhi.core.query.input.stream.single.SingleStreamRuntime;
import org.wso2.siddhi.core.query.input.stream.state.AbsentLogicalPostStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.AbsentLogicalPreStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.AbsentStreamPostStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.AbsentStreamPreStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.CountPostStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.CountPreStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.LogicalPostStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.LogicalPreStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.PreStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.StateStreamRuntime;
import org.wso2.siddhi.core.query.input.stream.state.StreamPostStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.StreamPreStateProcessor;
import org.wso2.siddhi.core.query.input.stream.state.receiver.PatternMultiProcessStreamReceiver;
import org.wso2.siddhi.core.query.input.stream.state.receiver.PatternSingleProcessStreamReceiver;
import org.wso2.siddhi.core.query.input.stream.state.receiver.SequenceMultiProcessStreamReceiver;
import org.wso2.siddhi.core.query.input.stream.state.receiver.SequenceSingleProcessStreamReceiver;
import org.wso2.siddhi.core.query.input.stream.state.runtime.CountInnerStateRuntime;
import org.wso2.siddhi.core.query.input.stream.state.runtime.EveryInnerStateRuntime;
import org.wso2.siddhi.core.query.input.stream.state.runtime.InnerStateRuntime;
import org.wso2.siddhi.core.query.input.stream.state.runtime.LogicalInnerStateRuntime;
import org.wso2.siddhi.core.query.input.stream.state.runtime.NextInnerStateRuntime;
import org.wso2.siddhi.core.query.input.stream.state.runtime.StreamInnerStateRuntime;
import org.wso2.siddhi.core.query.processor.SchedulingProcessor;
import org.wso2.siddhi.core.table.Table;
import org.wso2.siddhi.core.util.Scheduler;
import org.wso2.siddhi.core.util.SiddhiConstants;
import org.wso2.siddhi.core.util.statistics.LatencyTracker;
import io.siddhi.query.api.definition.AbstractDefinition;
import io.siddhi.query.api.execution.query.input.state.AbsentStreamStateElement;
import io.siddhi.query.api.execution.query.input.state.CountStateElement;
import io.siddhi.query.api.execution.query.input.state.EveryStateElement;
import io.siddhi.query.api.execution.query.input.state.LogicalStateElement;
import io.siddhi.query.api.execution.query.input.state.NextStateElement;
import io.siddhi.query.api.execution.query.input.state.StateElement;
import io.siddhi.query.api.execution.query.input.state.StreamStateElement;
import io.siddhi.query.api.execution.query.input.stream.BasicSingleInputStream;
import io.siddhi.query.api.execution.query.input.stream.StateInputStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to parse {@link StateStreamRuntime}
 */
public class StateInputStreamParser {


    public static StateStreamRuntime parseInputStream(StateInputStream stateInputStream,
                                                      SiddhiAppContext siddhiAppContext,
                                                      MetaStateEvent metaStateEvent,
                                                      Map<String, AbstractDefinition> streamDefinitionMap,
                                                      Map<String, AbstractDefinition> tableDefinitionMap,
                                                      Map<String, AbstractDefinition> windowDefinitionMap,
                                                      Map<String, AbstractDefinition> aggregationDefinitionMap,
                                                      Map<String, Table> tableMap,
                                                      List<VariableExpressionExecutor> variableExpressionExecutors,
                                                      LatencyTracker latencyTracker, String queryName) {

        Map<String, ProcessStreamReceiver> processStreamReceiverMap = new HashMap<String, ProcessStreamReceiver>();

        StateStreamRuntime stateStreamRuntime = new StateStreamRuntime(siddhiAppContext, metaStateEvent);

        String defaultLockKey = "";

        for (String streamId : stateInputStream.getAllStreamIds()) {
            int streamCount = stateInputStream.getStreamCount(streamId);
            if (streamCount == 1) {
                if (stateInputStream.getStateType() == StateInputStream.Type.SEQUENCE) {
                    processStreamReceiverMap.put(streamId, new SequenceSingleProcessStreamReceiver(streamId,
                            stateStreamRuntime, defaultLockKey, latencyTracker, queryName, siddhiAppContext));
                } else {
                    processStreamReceiverMap.put(streamId, new PatternSingleProcessStreamReceiver(streamId,
                            defaultLockKey, latencyTracker, queryName, siddhiAppContext));
                }
            } else {
                if (stateInputStream.getStateType() == StateInputStream.Type.SEQUENCE) {
                    processStreamReceiverMap.put(streamId, new SequenceMultiProcessStreamReceiver(streamId,
                            streamCount, stateStreamRuntime, latencyTracker, queryName, siddhiAppContext));
                } else {
                    processStreamReceiverMap.put(streamId, new PatternMultiProcessStreamReceiver(streamId,
                            streamCount, latencyTracker, queryName, siddhiAppContext));
                }
            }
        }

        StateElement stateElement = stateInputStream.getStateElement();
        List<PreStateProcessor> preStateProcessors = new ArrayList<>();
        InnerStateRuntime innerStateRuntime = parse(stateElement, streamDefinitionMap, tableDefinitionMap,
                windowDefinitionMap, aggregationDefinitionMap, tableMap, metaStateEvent,
                siddhiAppContext, variableExpressionExecutors, processStreamReceiverMap,
                null, null,
                stateInputStream.getStateType(),
                preStateProcessors, true, latencyTracker, queryName);

        stateStreamRuntime.setInnerStateRuntime(innerStateRuntime);

        if (stateInputStream.getWithinTime() != null) {
            List<Integer> startStateIdList = new ArrayList<>();
            for (PreStateProcessor preStateProcessor : preStateProcessors) {
                if (preStateProcessor.isStartState()) {
                    startStateIdList.add(preStateProcessor.getStateId());
                }
            }
            int[] startStateIds = Ints.toArray(startStateIdList);
            for (PreStateProcessor preStateProcessor : preStateProcessors) {
                preStateProcessor.setStartStateIds(startStateIds);
                preStateProcessor.setWithinTime(stateInputStream.getWithinTime().value());
            }
        }
        ((StreamPreStateProcessor) innerStateRuntime.getFirstProcessor()).setThisLastProcessor(
                (StreamPostStateProcessor) innerStateRuntime.getLastProcessor());

        return stateStreamRuntime;
    }

    private static InnerStateRuntime parse(StateElement stateElement,
                                           Map<String, AbstractDefinition> streamDefinitionMap,
                                           Map<String, AbstractDefinition> tableDefinitionMap,
                                           Map<String, AbstractDefinition> windowDefinitionMap,
                                           Map<String, AbstractDefinition> aggregationDefinitionMap,
                                           Map<String, Table> tableMap,
                                           MetaStateEvent metaStateEvent, SiddhiAppContext siddhiAppContext,
                                           List<VariableExpressionExecutor> variableExpressionExecutors,
                                           Map<String, ProcessStreamReceiver> processStreamReceiverMap,
                                           StreamPreStateProcessor streamPreStateProcessor,
                                           StreamPostStateProcessor streamPostStateProcessor,
                                           StateInputStream.Type stateType,
                                           List<PreStateProcessor> preStateProcessors,
                                           boolean isStartState, LatencyTracker latencyTracker, String queryName) {


        if (stateElement instanceof StreamStateElement) {

            BasicSingleInputStream basicSingleInputStream = ((StreamStateElement) stateElement)
                    .getBasicSingleInputStream();
            SingleStreamRuntime singleStreamRuntime = SingleInputStreamParser.parseInputStream(
                    basicSingleInputStream, siddhiAppContext, variableExpressionExecutors, streamDefinitionMap,
                    tableDefinitionMap, windowDefinitionMap, aggregationDefinitionMap, tableMap, metaStateEvent,
                    processStreamReceiverMap.get(basicSingleInputStream.getUniqueStreamIds().get(0)),
                    false, false, queryName);

            int stateIndex = metaStateEvent.getStreamEventCount() - 1;
            if (streamPreStateProcessor == null) {

                if (stateElement instanceof AbsentStreamStateElement) {

                    AbsentStreamPreStateProcessor absentProcessor = new AbsentStreamPreStateProcessor(stateType,
                            ((AbsentStreamStateElement) stateElement).getWaitingTime().value());

                    // Set the scheduler
                    siddhiAppContext.addEternalReferencedHolder(absentProcessor);
                    EntryValveProcessor entryValveProcessor = new EntryValveProcessor(siddhiAppContext);
                    entryValveProcessor.setToLast(absentProcessor);
                    Scheduler scheduler = SchedulerParser.parse(entryValveProcessor, siddhiAppContext);
                    absentProcessor.setScheduler(scheduler);

                    // Assign the AbsentStreamPreStateProcessor to streamPreStateProcessor
                    streamPreStateProcessor = absentProcessor;
                } else {
                    streamPreStateProcessor = new StreamPreStateProcessor(stateType);
                }
                streamPreStateProcessor.init(siddhiAppContext, queryName);
            }
            streamPreStateProcessor.setStateId(stateIndex);
            streamPreStateProcessor.setStartState(isStartState);
            streamPreStateProcessor.setNextProcessor(singleStreamRuntime.getProcessorChain());
            singleStreamRuntime.setProcessorChain(streamPreStateProcessor);
            if (streamPostStateProcessor == null) {
                if (stateElement instanceof AbsentStreamStateElement) {
                    streamPostStateProcessor = new AbsentStreamPostStateProcessor();
                } else {
                    streamPostStateProcessor = new StreamPostStateProcessor();
                }
            }
            streamPostStateProcessor.setStateId(stateIndex);
            singleStreamRuntime.getProcessorChain().setToLast(streamPostStateProcessor);
            streamPostStateProcessor.setThisStatePreProcessor(streamPreStateProcessor);
            streamPreStateProcessor.setThisStatePostProcessor(streamPostStateProcessor);
            streamPreStateProcessor.setThisLastProcessor(streamPostStateProcessor);

            StreamInnerStateRuntime innerStateRuntime = new StreamInnerStateRuntime(stateType);

            innerStateRuntime.setFirstProcessor(streamPreStateProcessor);
            innerStateRuntime.setLastProcessor(streamPostStateProcessor);
            innerStateRuntime.addStreamRuntime(singleStreamRuntime);
            preStateProcessors.add(streamPreStateProcessor);
            return innerStateRuntime;

        } else if (stateElement instanceof NextStateElement) {

            StateElement currentElement = ((NextStateElement) stateElement).getStateElement();
            InnerStateRuntime currentInnerStateRuntime = parse(currentElement, streamDefinitionMap,
                    tableDefinitionMap, windowDefinitionMap, aggregationDefinitionMap, tableMap, metaStateEvent,
                    siddhiAppContext, variableExpressionExecutors,
                    processStreamReceiverMap,
                    streamPreStateProcessor, streamPostStateProcessor,
                    stateType, preStateProcessors, isStartState, latencyTracker, queryName);

            StateElement nextElement = ((NextStateElement) stateElement).getNextStateElement();
            InnerStateRuntime nextInnerStateRuntime = parse(nextElement, streamDefinitionMap, tableDefinitionMap,
                    windowDefinitionMap, aggregationDefinitionMap, tableMap, metaStateEvent,
                    siddhiAppContext, variableExpressionExecutors, processStreamReceiverMap,
                    streamPreStateProcessor, streamPostStateProcessor, stateType, preStateProcessors,
                    false, latencyTracker, queryName);

            currentInnerStateRuntime.getLastProcessor().setNextStatePreProcessor(nextInnerStateRuntime
                    .getFirstProcessor());

            NextInnerStateRuntime nextStateRuntime = new NextInnerStateRuntime(currentInnerStateRuntime,
                    nextInnerStateRuntime, stateType);
            nextStateRuntime.setFirstProcessor(currentInnerStateRuntime.getFirstProcessor());
            nextStateRuntime.setLastProcessor(nextInnerStateRuntime.getLastProcessor());

            for (SingleStreamRuntime singleStreamRuntime : currentInnerStateRuntime.getSingleStreamRuntimeList()) {
                nextStateRuntime.addStreamRuntime(singleStreamRuntime);
            }
            for (SingleStreamRuntime singleStreamRuntime : nextInnerStateRuntime.getSingleStreamRuntimeList()) {
                nextStateRuntime.addStreamRuntime(singleStreamRuntime);
            }

            return nextStateRuntime;

        } else if (stateElement instanceof EveryStateElement) {

            StateElement currentElement = ((EveryStateElement) stateElement).getStateElement();

            List<PreStateProcessor> withinEveryPreStateProcessors = new ArrayList<>();
            InnerStateRuntime innerStateRuntime = parse(currentElement, streamDefinitionMap, tableDefinitionMap,
                    windowDefinitionMap, aggregationDefinitionMap, tableMap, metaStateEvent,
                    siddhiAppContext, variableExpressionExecutors, processStreamReceiverMap,
                    streamPreStateProcessor, streamPostStateProcessor, stateType,
                    withinEveryPreStateProcessors, isStartState, latencyTracker, queryName);

            EveryInnerStateRuntime everyInnerStateRuntime = new EveryInnerStateRuntime(innerStateRuntime, stateType);

            everyInnerStateRuntime.setFirstProcessor(innerStateRuntime.getFirstProcessor());
            everyInnerStateRuntime.setLastProcessor(innerStateRuntime.getLastProcessor());

            for (SingleStreamRuntime singleStreamRuntime : innerStateRuntime.getSingleStreamRuntimeList()) {
                everyInnerStateRuntime.addStreamRuntime(singleStreamRuntime);
            }
            everyInnerStateRuntime.getLastProcessor().setNextEveryStatePreProcessor(
                    everyInnerStateRuntime.getFirstProcessor());

            for (PreStateProcessor preStateProcessor : withinEveryPreStateProcessors) {
                preStateProcessor.setWithinEveryPreStateProcessor(everyInnerStateRuntime.getFirstProcessor());
            }
            preStateProcessors.addAll(withinEveryPreStateProcessors);
            return everyInnerStateRuntime;

        } else if (stateElement instanceof LogicalStateElement) {

            LogicalStateElement.Type type = ((LogicalStateElement) stateElement).getType();

            LogicalPreStateProcessor logicalPreStateProcessor1;
            if (((LogicalStateElement) stateElement).getStreamStateElement1() instanceof AbsentStreamStateElement) {
                logicalPreStateProcessor1 = new AbsentLogicalPreStateProcessor(type, stateType,
                        ((AbsentStreamStateElement) ((LogicalStateElement) stateElement)
                                .getStreamStateElement1()).getWaitingTime());

                // Set the scheduler
                siddhiAppContext.addEternalReferencedHolder((AbsentLogicalPreStateProcessor)
                        logicalPreStateProcessor1);
                EntryValveProcessor entryValveProcessor = new EntryValveProcessor(siddhiAppContext);
                entryValveProcessor.setToLast(logicalPreStateProcessor1);
                Scheduler scheduler = SchedulerParser.parse(entryValveProcessor, siddhiAppContext);
                ((SchedulingProcessor) logicalPreStateProcessor1).setScheduler(scheduler);
            } else {
                logicalPreStateProcessor1 = new LogicalPreStateProcessor(type, stateType);
            }
            logicalPreStateProcessor1.init(siddhiAppContext, queryName);
            LogicalPostStateProcessor logicalPostStateProcessor1;
            if (((LogicalStateElement) stateElement).getStreamStateElement1() instanceof AbsentStreamStateElement) {
                logicalPostStateProcessor1 = new AbsentLogicalPostStateProcessor(type);
            } else {
                logicalPostStateProcessor1 = new LogicalPostStateProcessor(type);
            }

            LogicalPreStateProcessor logicalPreStateProcessor2;
            if (((LogicalStateElement) stateElement).getStreamStateElement2() instanceof AbsentStreamStateElement) {
                logicalPreStateProcessor2 = new AbsentLogicalPreStateProcessor(type, stateType,
                        ((AbsentStreamStateElement) ((LogicalStateElement) stateElement).getStreamStateElement2())
                                .getWaitingTime());
                siddhiAppContext.addEternalReferencedHolder((AbsentLogicalPreStateProcessor)
                        logicalPreStateProcessor2);
                EntryValveProcessor entryValveProcessor = new EntryValveProcessor(siddhiAppContext);
                entryValveProcessor.setToLast(logicalPreStateProcessor2);
                Scheduler scheduler = SchedulerParser.parse(entryValveProcessor, siddhiAppContext);
                ((SchedulingProcessor) logicalPreStateProcessor2).setScheduler(scheduler);
            } else {
                logicalPreStateProcessor2 = new LogicalPreStateProcessor(type, stateType);
            }
            logicalPreStateProcessor2.init(siddhiAppContext, queryName);
            LogicalPostStateProcessor logicalPostStateProcessor2;
            if (((LogicalStateElement) stateElement).getStreamStateElement2() instanceof AbsentStreamStateElement) {
                logicalPostStateProcessor2 = new AbsentLogicalPostStateProcessor(type);
            } else {
                logicalPostStateProcessor2 = new LogicalPostStateProcessor(type);
            }

            logicalPostStateProcessor1.setPartnerPreStateProcessor(logicalPreStateProcessor2);
            logicalPostStateProcessor2.setPartnerPreStateProcessor(logicalPreStateProcessor1);

            logicalPostStateProcessor1.setPartnerPostStateProcessor(logicalPostStateProcessor2);
            logicalPostStateProcessor2.setPartnerPostStateProcessor(logicalPostStateProcessor1);

            logicalPreStateProcessor1.setPartnerStatePreProcessor(logicalPreStateProcessor2);
            logicalPreStateProcessor2.setPartnerStatePreProcessor(logicalPreStateProcessor1);

            StateElement stateElement2 = ((LogicalStateElement) stateElement).getStreamStateElement2();
            InnerStateRuntime innerStateRuntime2 = parse(stateElement2, streamDefinitionMap, tableDefinitionMap,
                    windowDefinitionMap, aggregationDefinitionMap, tableMap, metaStateEvent,
                    siddhiAppContext, variableExpressionExecutors, processStreamReceiverMap,
                    logicalPreStateProcessor2, logicalPostStateProcessor2,
                    stateType, preStateProcessors, isStartState, latencyTracker, queryName);

            StateElement stateElement1 = ((LogicalStateElement) stateElement).getStreamStateElement1();
            InnerStateRuntime innerStateRuntime1 = parse(stateElement1, streamDefinitionMap, tableDefinitionMap,
                    windowDefinitionMap, aggregationDefinitionMap, tableMap, metaStateEvent,
                    siddhiAppContext, variableExpressionExecutors, processStreamReceiverMap,
                    logicalPreStateProcessor1, logicalPostStateProcessor1, stateType,
                    preStateProcessors, isStartState, latencyTracker, queryName);


            LogicalInnerStateRuntime logicalInnerStateRuntime = new LogicalInnerStateRuntime(
                    innerStateRuntime1, innerStateRuntime2, stateType);

            logicalInnerStateRuntime.setFirstProcessor(innerStateRuntime1.getFirstProcessor());
            logicalInnerStateRuntime.setLastProcessor(innerStateRuntime2.getLastProcessor());

            for (SingleStreamRuntime singleStreamRuntime : innerStateRuntime2.getSingleStreamRuntimeList()) {
                logicalInnerStateRuntime.addStreamRuntime(singleStreamRuntime);
            }

            for (SingleStreamRuntime singleStreamRuntime : innerStateRuntime1.getSingleStreamRuntimeList()) {
                logicalInnerStateRuntime.addStreamRuntime(singleStreamRuntime);
            }

            return logicalInnerStateRuntime;

        } else if (stateElement instanceof CountStateElement) {

            int minCount = ((CountStateElement) stateElement).getMinCount();
            int maxCount = ((CountStateElement) stateElement).getMaxCount();
            if (minCount == SiddhiConstants.ANY) {
                minCount = 0;
            }
            if (maxCount == SiddhiConstants.ANY) {
                maxCount = Integer.MAX_VALUE;
            }

            CountPreStateProcessor countPreStateProcessor = new CountPreStateProcessor(minCount, maxCount, stateType);
            countPreStateProcessor.init(siddhiAppContext, queryName);
            CountPostStateProcessor countPostStateProcessor = new CountPostStateProcessor(minCount, maxCount);

            countPreStateProcessor.setCountPostStateProcessor(countPostStateProcessor);
            StateElement currentElement = ((CountStateElement) stateElement).getStreamStateElement();
            InnerStateRuntime innerStateRuntime = parse(currentElement, streamDefinitionMap, tableDefinitionMap,
                    windowDefinitionMap, aggregationDefinitionMap, tableMap, metaStateEvent,
                    siddhiAppContext, variableExpressionExecutors, processStreamReceiverMap,
                    countPreStateProcessor, countPostStateProcessor, stateType,
                    preStateProcessors, isStartState, latencyTracker, queryName);

            return new CountInnerStateRuntime((StreamInnerStateRuntime) innerStateRuntime);
        } else {
            throw new OperationNotSupportedException();
        }

    }

}
