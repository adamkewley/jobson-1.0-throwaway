/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.github.jobson.jobs.execution;

import com.github.jobson.TestConstants;
import com.github.jobson.TestHelpers;
import com.github.jobson.fixtures.PersistedJobRequestFixture;
import com.github.jobson.jobs.states.PersistedJobRequest;
import com.github.jobson.specs.JobSpec;
import com.github.jobson.utils.CancelablePromise;
import com.google.common.primitives.Bytes;
import com.github.jobson.api.v1.JobStatus;
import com.github.jobson.jobs.management.JobEventListeners;
import com.github.jobson.specs.ExecutionConfiguration;
import io.reactivex.functions.Action;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.github.jobson.jobs.management.JobEventListeners.createNullListeners;
import static com.github.jobson.jobs.management.JobEventListeners.createStderrListener;
import static com.github.jobson.jobs.management.JobEventListeners.createStdoutListener;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class JobExecutorTest {

    private static final PersistedJobRequest STANDARD_REQUEST;


    private static PersistedJobRequest readJobRequestFixture(String path) {
        final PersistedJobRequestFixture fixture =
                TestHelpers.readJSONFixture(path, PersistedJobRequestFixture.class);
        return fixture.toPersistedJobRequest();
    }

    private static <T> void promiseAssert(CancelablePromise<T> p, Consumer<T> resultHandler) throws Throwable {
        promiseAssert(p, resultHandler, () -> {});
    }

    private static <T> void promiseAssert(CancelablePromise<T> p, Consumer<T> resultHandler, Action afterSubscribing) throws Throwable {
        try {
            final Semaphore s = new Semaphore(1);
            s.acquire();

            final AtomicReference<Throwable> exHolder = new AtomicReference<>();

            p.thenAccept(resultHandler)
                    .thenAccept(v -> s.release())
                    .exceptionally(ex -> {
                        exHolder.set(ex);
                        s.release();
                        return null;
                    });

            afterSubscribing.run();

            if (s.tryAcquire(TestConstants.DEFAULT_TIMEOUT, MILLISECONDS)) {
                if (exHolder.get() != null) {
                    throw exHolder.get();
                }
            } else throw new TimeoutException("Timed out");
        } catch (InterruptedException ex) {}
    }

    private static PersistedJobRequest standardRequestWithCommand(String application, String... args) {
        final JobSpec existingSpec = STANDARD_REQUEST.getSpec();
        final ExecutionConfiguration existingConfig = existingSpec.getExecution();

        final Optional<List<String>> boxedArgs =
                args.length > 0 ? Optional.of(Arrays.asList(args)) : Optional.empty();

        final ExecutionConfiguration newConfig =
                new ExecutionConfiguration(
                        application,
                        boxedArgs,
                        existingConfig.getDependencies());

        final JobSpec newSpec =
                new JobSpec(
                        existingSpec.getId(),
                        existingSpec.getName(),
                        existingSpec.getDescription(),
                        existingSpec.getExpectedInputs(),
                        newConfig);

        return new PersistedJobRequest(
                STANDARD_REQUEST.getId(),
                STANDARD_REQUEST.getOwner(),
                STANDARD_REQUEST.getName(),
                STANDARD_REQUEST.getInputs(),
                STANDARD_REQUEST.getTimestamps(),
                newSpec);
    }


    static {
        STANDARD_REQUEST = readJobRequestFixture("fixtures/job/execution/valid-persisted-request.json");
    }


    protected abstract JobExecutor getInstance();




    @Test
    public void testExecuteReturnsExpectedPromiseForValidReq() throws Throwable {
        final JobExecutor jobExecutor = getInstance();
        final CancelablePromise<JobExecutionResult> ret =
                jobExecutor.execute(STANDARD_REQUEST, createNullListeners());

        assertThat(ret).isNotNull();

        promiseAssert(ret, result ->
                assertThat(result.getFinalStatus()).isEqualTo(JobStatus.FINISHED));
    }

    @Test
    public void testExecutePromiseResolvesWithFatalErrorForFailingCommand() throws Throwable {
        final JobExecutor jobExecutor = getInstance();
        final PersistedJobRequest req =
                standardRequestWithCommand("cat", "does-not-exist");
        final CancelablePromise<JobExecutionResult> ret =
                jobExecutor.execute(req, createNullListeners());

        promiseAssert(ret, result ->
                assertThat(result.getFinalStatus()).isEqualTo(JobStatus.FATAL_ERROR));
    }

    @Test
    public void testExecutePromiseResolvesWithAbortedIfPromiseIsCancelled() throws Throwable {
        final JobExecutor jobExecutor = getInstance();
        final PersistedJobRequest req =
                standardRequestWithCommand("cat"); // Long-running, because it blocks on an STDIN read.
        final CancelablePromise<JobExecutionResult> ret =
                jobExecutor.execute(req, createNullListeners());

        promiseAssert(
                ret,
                result -> assertThat(result.getFinalStatus()).isEqualTo(JobStatus.ABORTED),
                () -> ret.cancel(true));
    }

    @Test
    public void testExecuteWritesStdoutToTheStdoutListener() throws Throwable {
        final JobExecutor jobExecutor = getInstance();
        final String msgSuppliedToEcho = TestHelpers.generateRandomString();
        final PersistedJobRequest req =
                standardRequestWithCommand("echo", msgSuppliedToEcho);
        final AtomicReference<byte[]> bytesEchoedToStdout = new AtomicReference<>(new byte[]{});
        final Subject<byte[]> stdoutSubject = PublishSubject.create();

        stdoutSubject.subscribe(bytes ->
                bytesEchoedToStdout.getAndUpdate(existingBytes ->
                        Bytes.concat(existingBytes, bytes)));

        final Semaphore s = new Semaphore(1);
        s.acquire();
        stdoutSubject.doOnComplete(s::release).subscribe();

        final JobEventListeners listeners =
                createStdoutListener(stdoutSubject);

        jobExecutor.execute(req, listeners);

        s.tryAcquire(TestConstants.DEFAULT_TIMEOUT, MILLISECONDS);

        final String stringFromStdout = new String(bytesEchoedToStdout.get()).trim();
        assertThat(stringFromStdout).isEqualTo(msgSuppliedToEcho);
    }

    @Test
    public void testExecuteStdoutListenerIsCalledWithCompletedOnceApplicationExecutionEnds() throws Throwable {
        final JobExecutor jobExecutor = getInstance();
        final AtomicBoolean completedCalled = new AtomicBoolean(false);
        final Subject<byte[]> stdoutSubject = PublishSubject.create();
        stdoutSubject.doOnComplete(() -> completedCalled.set(true)).subscribe();
        final JobEventListeners listeners = createStdoutListener(stdoutSubject);
        final CancelablePromise<JobExecutionResult> ret =
                jobExecutor.execute(STANDARD_REQUEST, listeners);

        promiseAssert(ret, result -> {
            assertThat(completedCalled.get()).isTrue();
        });
    }

    @Test
    public void testExecuteWritesStderrToTheStderrListener() throws Throwable {
        final JobExecutor jobExecutor = getInstance();
        final String msgSuppliedToEcho = TestHelpers.generateRandomString();
        final String bashArg = "echo " + msgSuppliedToEcho + " 1>&2"; // TODO: Naughty.
        final PersistedJobRequest req =
                standardRequestWithCommand("bash", "-c", bashArg);
        final AtomicReference<byte[]> bytesEchoedToStderr = new AtomicReference<>(new byte[]{});
        final Subject<byte[]> stderrSubject = PublishSubject.create();

        stderrSubject.subscribe(bytes ->
                bytesEchoedToStderr.getAndUpdate(existingBytes ->
                        Bytes.concat(existingBytes, bytes)));

        final Semaphore s = new Semaphore(1);
        s.acquire();
        stderrSubject.doOnComplete(s::release).subscribe();

        final JobEventListeners listeners =
                createStderrListener(stderrSubject);

        jobExecutor.execute(req, listeners);

        s.tryAcquire(TestConstants.DEFAULT_TIMEOUT, MILLISECONDS);

        final String stringFromStderr = new String(bytesEchoedToStderr.get()).trim();
        assertThat(stringFromStderr).isEqualTo(msgSuppliedToEcho);
    }

    @Test
    public void testExecuteStderrListenerIsCompletedOnceApplicationExecutionEnds() throws Throwable {
        final JobExecutor jobExecutor = getInstance();
        final AtomicBoolean completedCalled = new AtomicBoolean(false);
        final Subject<byte[]> stderrSubject = PublishSubject.create();
        stderrSubject.doOnComplete(() -> completedCalled.set(true)).subscribe();
        final JobEventListeners listeners = createStderrListener(stderrSubject);
        final CancelablePromise<JobExecutionResult> ret =
                jobExecutor.execute(STANDARD_REQUEST, listeners);

        promiseAssert(ret, result -> assertThat(completedCalled.get()).isTrue());
    }
}