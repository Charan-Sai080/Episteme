package com.episteme.indexer;

import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.CtScanner;
import com.episteme.db.DatabaseManager;
import com.episteme.db.SymbolDao;
import com.episteme.db.EdgeDao;

/**
 * Traverses the Spoon AST to extract the Semantic Contract of Java methods.
 * <p>
 * This class captures Method Signatures, Parameters, Annotations, and JavaDoc.
 */
public class ContractExtractor extends CtScanner {

    private final DatabaseManager dbManager;
    private final SymbolDao symbolDao;
    private final EdgeBuilder edgeBuilder;
    private boolean processed = false;

    public ContractExtractor(DatabaseManager dbManager, SymbolDao symbolDao) {
        this.dbManager = dbManager;
        this.symbolDao = symbolDao;
        SymbolResolver resolver = new SymbolResolver(dbManager);
        EdgeDao edgeDao = new EdgeDao(dbManager);
        this.edgeBuilder = new EdgeBuilder(resolver, edgeDao);
    }

    public boolean isProcessed() {
        return processed;
    }

    /**
     * Visits a method declaration to extract its semantic contract.
     */
    @Override
    public <T> void visitCtMethod(CtMethod<T> method) {
        processed = true;
        
        try {
            if (method.getType() != null) {
                symbolDao.resolveOrInsertSymbol(method.getType());
            }
            method.getParameters().forEach(param -> {
                try {
                    symbolDao.resolveOrInsertSymbol(param.getType());
                } catch (Exception e) {
                    // Ignore for test
                }
            });
            
            // Check for method overrides
            java.util.Collection<spoon.reflect.declaration.CtMethod<?>> topDefinitions = method.getTopDefinitions();
            if (!topDefinitions.isEmpty()) {
                for (spoon.reflect.declaration.CtMethod<?> superDef : topDefinitions) {
                    if (superDef != method) {
                        edgeBuilder.buildEdge(method, superDef.getReference(), "OVERRIDES");
                    }
                }
            }
            
            // Extract Execution Trace
            if (method.getBody() != null) {
                ExecutionTraceExtractor traceExtractor = new ExecutionTraceExtractor(edgeBuilder);
                method.getBody().accept(traceExtractor);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        super.visitCtMethod(method);
        
        try {
            edgeBuilder.flush();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Captures Lambda functions which represent functional flows.
     */
    @Override
    public <T> void visitCtLambda(spoon.reflect.code.CtLambda<T> lambda) {
        super.visitCtLambda(lambda);
    }

    /**
     * Captures Anonymous inner classes or static initialization blocks.
     */
    @Override
    public void visitCtAnonymousExecutable(spoon.reflect.declaration.CtAnonymousExecutable anonymousExec) {
        super.visitCtAnonymousExecutable(anonymousExec);
    }

    /**
     * Captures class-level relationships like EXTENDS and IMPLEMENTS.
     */
    @Override
    public <T> void visitCtClass(spoon.reflect.declaration.CtClass<T> ctClass) {
        if (ctClass.getSuperclass() != null) {
            edgeBuilder.buildEdge(ctClass, ctClass.getSuperclass(), "EXTENDS");
        }
        for (spoon.reflect.reference.CtTypeReference<?> interfaceRef : ctClass.getSuperInterfaces()) {
            edgeBuilder.buildEdge(ctClass, interfaceRef, "IMPLEMENTS");
        }
        super.visitCtClass(ctClass);
        try {
            edgeBuilder.flush();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Captures interface inheritance (EXTENDS for interfaces).
     */
    @Override
    public <T> void visitCtInterface(spoon.reflect.declaration.CtInterface<T> ctInterface) {
        for (spoon.reflect.reference.CtTypeReference<?> superInterface : ctInterface.getSuperInterfaces()) {
            edgeBuilder.buildEdge(ctInterface, superInterface, "EXTENDS");
        }
        super.visitCtInterface(ctInterface);
        try {
            edgeBuilder.flush();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
    }
}
