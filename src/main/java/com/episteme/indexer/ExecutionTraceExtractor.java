package com.episteme.indexer;

import spoon.reflect.code.CtBlock;
import spoon.reflect.visitor.CtScanner;

/**
 * Extracts the Execution Trace (Reads, Writes, Calls) from a method's body.
 */
public class ExecutionTraceExtractor extends CtScanner {

    private final EdgeBuilder edgeBuilder;

    public ExecutionTraceExtractor(EdgeBuilder edgeBuilder) {
        this.edgeBuilder = edgeBuilder;
    }
    @Override
    public <R> void visitCtBlock(CtBlock<R> block) {
        super.visitCtBlock(block);
    }

    /**
     * Traces method invocations (CALLS).
     */
    @Override
    public <T> void visitCtInvocation(spoon.reflect.code.CtInvocation<T> invocation) {
        if (edgeBuilder != null && invocation.getExecutable() != null) {
            spoon.reflect.declaration.CtExecutable<?> parentMethod = invocation.getParent(spoon.reflect.declaration.CtExecutable.class);
            if (parentMethod != null) {
                edgeBuilder.buildEdge(parentMethod, invocation.getExecutable(), "CALLS");
            }
        }
        super.visitCtInvocation(invocation);
    }

    /**
     * Traces object instantiations (CALLS to constructors).
     */
    @Override
    public <T> void visitCtConstructorCall(spoon.reflect.code.CtConstructorCall<T> ctConstructorCall) {
        if (edgeBuilder != null && ctConstructorCall.getExecutable() != null) {
            spoon.reflect.declaration.CtExecutable<?> parentMethod = ctConstructorCall.getParent(spoon.reflect.declaration.CtExecutable.class);
            if (parentMethod != null) {
                edgeBuilder.buildEdge(parentMethod, ctConstructorCall.getExecutable(), "CALLS");
            }
        }
        super.visitCtConstructorCall(ctConstructorCall);
    }

    /**
     * Traces variable reads (READS).
     */
    @Override
    public <T> void visitCtVariableRead(spoon.reflect.code.CtVariableRead<T> variableRead) {
        if (edgeBuilder != null && variableRead.getVariable() != null) {
            // Only care about field reads, not local variables, to keep graph focused on architecture
            if (variableRead.getVariable() instanceof spoon.reflect.reference.CtFieldReference) {
                spoon.reflect.declaration.CtExecutable<?> parentMethod = variableRead.getParent(spoon.reflect.declaration.CtExecutable.class);
                if (parentMethod != null) {
                    edgeBuilder.buildEdge(parentMethod, variableRead.getVariable(), "READS");
                }
            }
        }
        super.visitCtVariableRead(variableRead);
    }

    /**
     * Traces variable writes/assignments (WRITES).
     */
    @Override
    public <T> void visitCtVariableWrite(spoon.reflect.code.CtVariableWrite<T> variableWrite) {
        if (edgeBuilder != null && variableWrite.getVariable() != null) {
            if (variableWrite.getVariable() instanceof spoon.reflect.reference.CtFieldReference) {
                spoon.reflect.declaration.CtExecutable<?> parentMethod = variableWrite.getParent(spoon.reflect.declaration.CtExecutable.class);
                if (parentMethod != null) {
                    edgeBuilder.buildEdge(parentMethod, variableWrite.getVariable(), "WRITES");
                }
            }
        }
        super.visitCtVariableWrite(variableWrite);
    }

    /**
     * Captures Lambda functions inside blocks to trace logic buried in Java Streams.
     */
    @Override
    public <T> void visitCtLambda(spoon.reflect.code.CtLambda<T> lambda) {
        super.visitCtLambda(lambda);
    }
}
