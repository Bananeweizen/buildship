/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.util.progress;

import java.util.concurrent.TimeUnit;

import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;

import com.google.common.base.Preconditions;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.buildship.core.CorePlugin;

/**
 * Job that belongs to the Gradle job family.
 *
 * @param <T> the result type of the operation the job executes
 */
public abstract class ToolingApiJob<T> extends WorkspaceJob {

    // TODO (donat) rename package to org.eclipse.buildship.core.operation

    private final CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();

    private ToolingApiJobResultHandler<T> resultHandler = new DefaultResultHandler<T>();

    public ToolingApiJob(String name) {
        super(name);
    }

    public void setResultHandler(ToolingApiJobResultHandler<T> resultHandler) {
        this.resultHandler = Preconditions.checkNotNull(resultHandler);
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        final IProgressMonitor efficientMonitor = new RateLimitingProgressMonitor(monitor, 500, TimeUnit.MILLISECONDS);

     // TODO (donat) execute as IWorkspaceRunnable
        try {
            T result = runInToolingApi(efficientMonitor);
            this.resultHandler.onSuccess(result);
        } catch (Exception e) {
            this.resultHandler.onFailure(ToolingApiStatus.from(getName(), e));
        }
        return Status.OK_STATUS;
    }

    /**
     * Method to be executed in {@link Job#run(IProgressMonitor)}.
     *
     * @param monitor the monitor to report progress on
     * @return the job result passed to the {@link ToolingApiJobResultHandler}.
     * @throws Exception if an error occurs
     */
    public abstract T runInToolingApi(IProgressMonitor monitor) throws Exception;

    protected CancellationTokenSource getTokenSource() {
        return this.tokenSource;
    }

    protected CancellationToken getToken() {
        return this.tokenSource.token();
    }

    @Override
    public boolean belongsTo(Object family) {
        return CorePlugin.GRADLE_JOB_FAMILY.equals(family);
    }

    @Override
    protected void canceling() {
        this.tokenSource.cancel();
    }

    /**
     * Default handler for the target operation.
     *
     * @param <T> the result type
     */
    private static final class DefaultResultHandler<T> implements ToolingApiJobResultHandler<T> {

        public DefaultResultHandler() {
        }

        @Override
        public void onSuccess(T result) {
            // do nothing
        }

        @Override
        public void onFailure(ToolingApiStatus status) {
            status.handleDefault();
        }
    }
}
