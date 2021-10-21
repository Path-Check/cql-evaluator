package org.opencds.cqf.cql.evaluator.measure;

import java.util.EnumSet;

import org.opencds.cqf.cql.engine.execution.CqlEngine;

public class MeasureEvalConfig {
    public static MeasureEvalConfig defaultConfig() {
        return new MeasureEvalConfig();
    }

    private Boolean asyncEnabled = false;
    private Integer asyncThreshold = 1000;

    private Boolean parallelEnabled = false;
    private Integer parallelThreshold = 200;
    private Integer parallelThreads = null;

    // private Boolean distributedEnabled = false;
    // private Integer distributedThreshold = 5000;

    private Boolean debugLoggingEnabled = false;

    private EnumSet<CqlEngine.Options> cqlEngineOptions;

    public EnumSet<CqlEngine.Options> getCqlEngineOptions() {
        if (this.cqlEngineOptions == null) {
            this.cqlEngineOptions = EnumSet.of(CqlEngine.Options.EnableExpressionCaching);
        }

        return this.cqlEngineOptions; 
    }

    public void setCqlEngineOptions(EnumSet<CqlEngine.Options> cqlEngineOptions) {
        this.cqlEngineOptions = cqlEngineOptions;
    }

    public Boolean getDebugLoggingEnabled() {
        return this.debugLoggingEnabled;
    }

    public void setDebugLoggingEnabled(Boolean value) {
        this.debugLoggingEnabled = value;
    }

    public Boolean getAsyncEnabled() {
        return this.asyncEnabled;
    }

    public void setAsyncEnabled(Boolean value) {
        this.asyncEnabled = value;
    }

    public Integer getAsyncThreshold() {
        return this.asyncThreshold;
    }

    public void setAsyncThreshold(Integer value) {
        this.asyncThreshold = value;
    }

    public Boolean getParallelEnabled() {
        return this.parallelEnabled;
    }

    public void setParallelEnabled(Boolean value) {
        this.parallelEnabled = value;
    }

    public Integer getParallelThreshold() {
        return this.parallelThreshold;
    }

    public void setParallelThreshold(Integer value) {
        this.parallelThreshold = value;
    }

    public Integer getParallelThreads() {
        if (this.parallelThreads == null) {
            return Runtime.getRuntime().availableProcessors();
        }

        return this.parallelThreads;
    }

    public void setParallelThreads(Integer value) {
        this.parallelThreads = value;
    }

    // TODO: This probably something that's implemented at the platform level
    // public Boolean getDistributedEnabled() {
    //     return this.distributedEnabled;
    // }

    // public void setDistributedEnabled(Boolean value) {
    //     this.distributedEnabled = value;
    // }

    // public Integer getDistributedThreshold() {
    //     return this.distributedThreshold;
    // }

    // public void setDistributedThreshold(Integer value) {
    //     this.distributedThreshold = value;
    // }
}
