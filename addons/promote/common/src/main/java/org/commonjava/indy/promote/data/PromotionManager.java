/**
 * Copyright (C) 2011-2022 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.promote.data;

import org.apache.commons.lang3.StringUtils;
import org.commonjava.cdi.util.weft.DrainingExecutorCompletionService;
import org.commonjava.cdi.util.weft.ExecutorConfig;
import org.commonjava.cdi.util.weft.Locker;
import org.commonjava.cdi.util.weft.ThreadContext;
import org.commonjava.cdi.util.weft.WeftExecutorService;
import org.commonjava.cdi.util.weft.WeftManaged;
import org.commonjava.indy.IndyWorkflowException;
import org.commonjava.indy.audit.ChangeSummary;
import org.commonjava.indy.content.ContentManager;
import org.commonjava.indy.content.DownloadManager;
import org.commonjava.indy.core.inject.GroupMembershipLocks;
import org.commonjava.indy.data.IndyDataException;
import org.commonjava.indy.data.StoreDataManager;
import org.commonjava.o11yphant.metrics.annotation.Measure;
import org.commonjava.indy.model.core.ArtifactStore;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.HostedRepository;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.model.core.StoreType;
import org.commonjava.indy.promote.callback.PromotionCallbackHelper;
import org.commonjava.indy.promote.change.event.PathsPromoteCompleteEvent;
import org.commonjava.indy.promote.change.event.PromoteCompleteEvent;
import org.commonjava.indy.promote.conf.PromoteConfig;
import org.commonjava.indy.promote.model.GroupPromoteRequest;
import org.commonjava.indy.promote.model.GroupPromoteResult;
import org.commonjava.indy.promote.model.PathsPromoteRequest;
import org.commonjava.indy.promote.model.PathsPromoteResult;
import org.commonjava.indy.promote.model.ValidationResult;
import org.commonjava.indy.promote.validate.PromotionValidationException;
import org.commonjava.indy.promote.validate.PromotionValidator;
import org.commonjava.indy.promote.validate.model.ValidationRequest;
import org.commonjava.indy.util.ValuePipe;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.model.SpecialPathInfo;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.spi.io.SpecialPathManager;
import org.commonjava.maven.galley.spi.nfc.NotFoundCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.commonjava.indy.util.RequestContextHelper;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.commonjava.indy.change.EventUtils.fireEvent;
import static org.commonjava.indy.core.ctl.PoolUtils.detectOverload;
import static org.commonjava.indy.core.ctl.PoolUtils.detectOverloadVoid;
import static org.commonjava.indy.data.StoreDataManager.AFFECTED_GROUPS;
import static org.commonjava.indy.data.StoreDataManager.IGNORE_READONLY;
import static org.commonjava.indy.data.StoreDataManager.TARGET_STORE;
import static org.commonjava.indy.model.core.StoreType.hosted;
import static org.commonjava.indy.promote.data.PromotionHelper.throwProperException;
import static org.commonjava.indy.promote.data.PromotionHelper.timeInMillSeconds;
import static org.commonjava.indy.promote.data.PromotionHelper.timeInSeconds;
import static org.commonjava.indy.promote.util.Batcher.batch;
import static org.commonjava.indy.promote.util.Batcher.getParalleledBatchSize;
import static org.commonjava.maven.galley.model.TransferOperation.UPLOAD;

/**
 * Component responsible for orchestrating the transfer of artifacts from one store to another
 * according to the given {@link PathsPromoteRequest} or {@link PathsPromoteResult}.
 *
 * @author jdcasey
 */
@ApplicationScoped
public class PromotionManager
{
    private static final String PROMOTION_ID = "promotion-id";

    private static final String PROMOTION_TYPE = "promotion-type";

    private static final String PATH_PROMOTION = "path";

    private static final String GROUP_PROMOTION = "group";

    private static final String PROMOTION_SOURCE = "promotion-source";

    private static final String PROMOTION_TARGET = "promotion-target";

    private static final String PROMOTION_CONTENT_PATH = "promotion-content-path";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private PromoteConfig config;

    @Inject
    private ContentManager contentManager;

    @Inject
    private DownloadManager downloadManager;

    @Inject
    private StoreDataManager storeManager;

    @Inject
    private PromotionValidator validator;

    @Inject
    private Event<PromoteCompleteEvent> promoteCompleteEvent;

    @Inject
    private PathConflictManager conflictManager;

    @Inject
    private SpecialPathManager specialPathManager;

    @GroupMembershipLocks
    @Inject
    private Locker<StoreKey> byGroupTargetLocks;

    private final Map<String, StoreKey> targetGroupKeyMap = new ConcurrentHashMap<>( 1 );

    @WeftManaged
    @Inject
    @ExecutorConfig( named = "promotion", threads = 8, priority = 8, loadSensitive = ExecutorConfig.BooleanLiteral.TRUE )
    private WeftExecutorService asyncPromotionService;

    @WeftManaged
    @Inject
    @ExecutorConfig( named = "promotion-transfers", threads = 40, priority = 6, loadSensitive = ExecutorConfig.BooleanLiteral.TRUE,
                     maxLoadFactor = 100 )
    private WeftExecutorService transferService;

    @Inject
    private PromotionCallbackHelper callbackHelper;

    @Inject
    private PromotionHelper promotionHelper;

    protected PromotionManager()
    {
    }

    public PromotionManager( PromotionValidator validator, ContentManager contentManager,
                             DownloadManager downloadManager, StoreDataManager storeManager,
                             Locker<StoreKey> byGroupTargetLocks,
                             PromoteConfig config, NotFoundCache nfc, WeftExecutorService asyncPromotionService,
                             WeftExecutorService transferService,
                             SpecialPathManager specialPathManager )
    {
        this.validator = validator;
        this.contentManager = contentManager;
        this.downloadManager = downloadManager;
        this.storeManager = storeManager;
        this.byGroupTargetLocks = byGroupTargetLocks;
        this.config = config;
        this.asyncPromotionService = asyncPromotionService;
        this.transferService = transferService;
        this.promotionHelper = new PromotionHelper( storeManager, downloadManager, contentManager, nfc );
        this.conflictManager = new PathConflictManager();
        this.specialPathManager = specialPathManager;
    }

    @Measure
    public GroupPromoteResult promoteToGroup( GroupPromoteRequest request, String user, String baseUrl )
            throws PromotionException, IndyWorkflowException
    {
        RequestContextHelper.setContext( PROMOTION_ID, request.getPromotionId() );
        RequestContextHelper.setContext( PROMOTION_TYPE, GROUP_PROMOTION );
        RequestContextHelper.setContext( PROMOTION_SOURCE, request.getSource().toString() );
        RequestContextHelper.setContext( PROMOTION_TARGET, request.getTargetKey().toString() );

        if ( !storeManager.hasArtifactStore( request.getSource() ) )
        {
            String error = String.format( "Cannot promote from missing source: %s", request.getSource() );
            logger.warn( error );

            return new GroupPromoteResult( request, error );
        }

        final StoreKey targetKey = getTargetKey( request.getTarget().getName(), request.getTarget().getPackageType() );

        if ( !storeManager.hasArtifactStore( targetKey ) )
        {
            String error = String.format( "No such target group: %s.", request.getTarget() );
            logger.warn( error );

            return new GroupPromoteResult( request, error );
        }

        Future<GroupPromoteResult> future = submitGroupPromoteRequest( request, user, baseUrl );
        if ( request.isAsync() )
        {
            return new GroupPromoteResult( request ).accepted();
        }
        else
        {
            try
            {
                return future.get();
            }
            catch ( InterruptedException | ExecutionException e )
            {
                logger.error( "Group promotion failed: " + request.getSource() + " -> " + request.getTargetKey(), e );
                throw new PromotionException( "Execution of group promotion failed.", e );
            }
        }
    }

    private ValidationResult doValidationAndPromote( GroupPromoteRequest request, AtomicReference<Exception> error,
                                                     String user, String baseUrl )
    {
        ValidationResult validation = new ValidationResult();
        logger.info( "Running validations for promotion of: {} to group: {}", request.getSource(),
                     request.getTarget() );

        final StoreKey targetKey = getTargetKey( request.getTarget().getName(), request.getTarget().getPackageType() );
        byGroupTargetLocks.lockAnd( targetKey, config.getLockTimeoutSeconds(), k -> {
            Group target;
            try
            {
                target = (Group) storeManager.getArtifactStore( request.getTargetKey() );
            }
            catch ( IndyDataException e )
            {
                error.set( new PromotionException( "Cannot retrieve target group: %s. Reason: %s", e,
                                                   request.getTarget(), e.getMessage() ) );
                return null;
            }

            try
            {
                ValidationRequest validationRequest = validator.validate( request, validation, baseUrl );

                if ( validation.isValid() )
                {
                    if ( !request.isDryRun() && !target.getConstituents().contains( request.getSource() ) )
                    {
                        // give the preUpdate event a different object to compare vs. the original group.
                        target = target.copyOf();
                        target.addConstituent( request.getSource() );
                        try
                        {
                            final ChangeSummary changeSummary = new ChangeSummary( user,
                                                                                   "Promoting " + request.getSource()
                                                                                                   + " into membership of group: "
                                                                                                   + target.getKey() );

                            storeManager.storeArtifactStore( target, changeSummary, false, true, new EventMetadata() );
                            final Group targetForNfcCleaning = target;
                            final String name = String.format( "PromoteNFCClean-method(%s)-source(%s)-target(%s)",
                                                               "doValidationAndPromote", validationRequest.getSource(),
                                                               targetForNfcCleaning.getKey() );
                            final String context = String.format( "Class: %s, method: %s, source: %s, target: %s",
                                                                  this.getClass().getName(), "doValidationAndPromote",
                                                                  validationRequest.getSource(),
                                                                  targetForNfcCleaning.getKey() );
                            storeManager.asyncGroupAffectedBy( new StoreDataManager.ContextualTask( name, context, () -> {
                                try
                                {
                                    promotionHelper.clearStoreNFC( validationRequest.getSourcePaths(),
                                                                   targetForNfcCleaning, null );
                                }
                                catch ( PromotionValidationException e )
                                {
                                    logger.warn( "Error happened for clear nfc during promote validation: {}",
                                                 e.getMessage() );
                                }
                            } ) );

                            if ( hosted == request.getSource().getType() && config.isAutoLockHostedRepos() )
                            {
                                HostedRepository source =
                                                (HostedRepository) storeManager.getArtifactStore( request.getSource() );

                                source.setReadonly( true );

                                final ChangeSummary readOnlySummary = new ChangeSummary( user, "Promoting "
                                                + request.getSource() + " into membership of group: " + target.getKey() );

                                storeManager.storeArtifactStore( source, readOnlySummary, false, true,
                                                                 new EventMetadata() );
                            }
                        }
                        catch ( IndyDataException e )
                        {
                            error.set( new PromotionException(
                                    "Failed to store group: %s with additional member: %s. Reason: %s", e,
                                    target.getKey(), request.getSource(), e.getMessage() ) );
                        }
                    }
                }
            }
            catch ( PromotionValidationException | IndyWorkflowException e )
            {
                error.set( e );
            }

            return null;
        }, ( k, lock ) -> {
            //FIXME: should we consider to repeat the promote process several times when lock failed?
            String errorMsg =
                    String.format( "Failed to acquire group promotion lock on target when promote: %s in %d seconds.",
                                   targetKey, config.getLockTimeoutSeconds() );
            logger.error( errorMsg );
            error.set( new PromotionException( errorMsg ) );

            return Boolean.FALSE;
        } );

        return validation;
    }

    /**
     * Provides target group store key for a given group name. This is meant to provide the same instance of the key
     * for a name to be able to synchronize promotion requests based on this instance.
     *
     * @param targetName the target group name
     * @return the group store key
     */
    private StoreKey getTargetKey( final String targetName, final String packageType )
    {
        return targetGroupKeyMap.computeIfAbsent( packageType + "-" + targetName,
                                                  k -> new StoreKey( packageType, StoreType.group, targetName ) );
    }

    public GroupPromoteResult rollbackGroupPromote( GroupPromoteResult result, String user )
            throws PromotionException, IndyWorkflowException
    {
        GroupPromoteRequest request = result.getRequest();

        if ( !storeManager.hasArtifactStore( request.getSource() ) )
        {
            String error = String.format( "No such source/member store: %s", request.getSource() );
            logger.warn( error );

            return new GroupPromoteResult( request, error );
        }

        Group target;
        try
        {
            target = (Group) storeManager.getArtifactStore( request.getTargetKey() );
        }
        catch ( IndyDataException e )
        {
            throw new PromotionException( "Cannot retrieve target group: %s. Reason: %s", e, request.getTarget(),
                                          e.getMessage() );
        }

        if ( target == null )
        {
            String error = String.format( "No such target group: %s.", request.getTarget() );
            logger.warn( error );

            return new GroupPromoteResult( request, error );
        }

        Future<GroupPromoteResult> future = submitGroupPromoteRollback( result, target, user );
        if ( request.isAsync() )
        {
            return new GroupPromoteResult( request ).accepted();
        }
        else
        {
            try
            {
                return future.get();
            }
            catch ( InterruptedException | ExecutionException e )
            {
                logger.error( "Group promotion rollback failed: " + request.getSource() + " -> " + request.getTargetKey(), e );
                throw new PromotionException( "Execution of group promotion rollback failed.", e );
            }
        }
    }

    private GroupPromoteResult doGroupPromoteRollback( GroupPromoteResult result, Group target, String user )
            throws PromotionException
    {
        GroupPromoteResult ret;

        GroupPromoteRequest request = result.getRequest();

        if ( target.getConstituents().contains( request.getSource() ) )
        {
            // give the preUpdate event a different object to compare vs. the original group.
            target = target.copyOf();

            target.removeConstituent( request.getSource() );
            try
            {
                final ChangeSummary changeSummary = new ChangeSummary( user, "Removing " + request.getSource()
                                + " from membership of group: " + target.getKey() );

                storeManager.storeArtifactStore( target, changeSummary, false, true, new EventMetadata() );
            }
            catch ( IndyDataException e )
            {
                throw new PromotionException( "Failed to store group: %s with additional member: %s. Reason: %s", e,
                                              target.getKey(), request.getSource(), e.getMessage() );
            }
            ret = new GroupPromoteResult( request );
        }
        else
        {
            ret = new GroupPromoteResult( request, "Group: " + target.getKey() + " does not contain member: "
                    + request.getSource() );
        }

        return ret;
    }

    private Future<GroupPromoteResult> submitGroupPromoteRollback( final GroupPromoteResult result, final Group target, final String user )
            throws IndyWorkflowException
    {
        return detectOverload( () -> asyncPromotionService.submit( () -> {
            GroupPromoteResult ret;
            try
            {
                ret = doGroupPromoteRollback( result, target, user );
            }
            catch ( Exception ex )
            {
                GroupPromoteRequest request = result.getRequest();
                String msg = "Group promotion rollback failed. Target: " + target.getKey() + ", Source: "
                        + request.getSource() + ", Reason: " + getStackTrace( ex );
                logger.warn( msg );
                ret = new GroupPromoteResult( request, msg );
            }

            if ( ret.getRequest().getCallback() != null )
            {
                return callbackHelper.callback( ret.getRequest().getCallback(), ret );
            }

            return ret;
        } ) );
    }

    private Future<GroupPromoteResult> submitGroupPromoteRequest( final GroupPromoteRequest request, final String user, final String baseUrl )
            throws IndyWorkflowException
    {
        return detectOverload( () -> asyncPromotionService.submit( () -> {
            AtomicReference<Exception> error = new AtomicReference<>();
            ValidationResult validation = doValidationAndPromote( request, error, user, baseUrl );

            GroupPromoteResult ret;
            Exception ex = error.get();
            if ( ex != null )
            {
                String msg = "Group promotion failed. Target: " + request.getTarget() + ", Source: "
                        + request.getSource() + ", Reason: " + getStackTrace( ex );
                logger.warn( msg );
                ret = new GroupPromoteResult( request, msg );
            }
            else
            {
                ret = new GroupPromoteResult( request, validation );
            }

            if ( request.getCallback() != null )
            {
                return callbackHelper.callback( ret.getRequest().getCallback(), ret );
            }

            return ret;
        } ) );
    }


    /**
     * Promote artifacts from the source to the target given the {@link PathsPromoteRequest}. If paths are given, promote them.
     * Otherwise, build a recursive list of available artifacts in the source store and promote them.
     *
     * @param request containing source and target store keys, and an optional list of paths
     * @return The result including the source and target store keys, the paths completed (promoted successfully),
     * or errors explaining what (if anything) went wrong.
     *
     * IMPORTANT: Since 1.8, we use all-or-nothing policy, i.e., if anything fails we revert previous promoted paths.
     */
    @Measure
    public PathsPromoteResult promotePaths( final PathsPromoteRequest request, final String baseUrl )
            throws PromotionException, IndyWorkflowException
    {
        RequestContextHelper.setContext( PROMOTION_ID, request.getPromotionId() );
        RequestContextHelper.setContext( PROMOTION_TYPE, PATH_PROMOTION );
        RequestContextHelper.setContext( PROMOTION_SOURCE, request.getSource().toString() );
        RequestContextHelper.setContext( PROMOTION_TARGET, request.getTargetKey().toString() );

        Future<PathsPromoteResult> future = submitPathsPromoteRequest( request, baseUrl );
        if ( request.isAsync() )
        {
            return new PathsPromoteResult( request ).accepted();
        }
        else
        {
            try
            {
                return future.get();
            }
            catch ( InterruptedException | ExecutionException e )
            {
                logger.error( "Path promotion failed: " + request.getSource() + " -> " + request.getTargetKey(), e );
                throw new PromotionException( "Execution of path promotion failed.", e );
            }
        }
    }

    private Future<PathsPromoteResult> submitPathsPromoteRequest( PathsPromoteRequest request, final String baseUrl )
                    throws IndyWorkflowException
    {
        return detectOverload( () -> asyncPromotionService.submit( () -> {
            PathsPromoteResult ret;
            try
            {
                ret = doPathsPromotion( request, false, baseUrl );
            }
            catch ( Exception ex )
            {
                String msg = "Path promotion failed. Target: " + request.getTarget() + ", Source: "
                                + request.getSource() + ", Reason: " + getStackTrace( ex );
                logger.warn( msg );
                ret = new PathsPromoteResult( request, msg );
            }

            if ( ret.getRequest().getCallback() != null )
            {
                return callbackHelper.callback( ret.getRequest().getCallback(), ret );
            }

            return ret;
        } ) );
    }

    /**
     * Attempt to rollback a previous {@link PathsPromoteResult}.
     *
     * All paths in the completed paths set are deleted from the target. The output {@link PathsPromoteResult} contains
     * the previous content, with removed target paths moved from the completed list to the pending list.
     *
     * @param result The result to rollback
     * @return The same result, with successful deletions moved from the completed to pending paths list,
     * and the error cleared (or set to a new error)
     */
    public PathsPromoteResult rollbackPathsPromote( final PathsPromoteResult result )
                    throws PromotionException, IndyWorkflowException
    {
        final PathsPromoteRequest request = result.getRequest();

        Future<PathsPromoteResult> future = submitRollbackPathsPromote( result );
        if ( request.isAsync() )
        {
            return new PathsPromoteResult( request ).accepted();
        }
        else
        {
            try
            {
                return future.get();
            }
            catch ( InterruptedException | ExecutionException e )
            {
                logger.error( "Path promotion rollback failed. From (target): " + request.getTarget()
                                              + ", to (source): " + request.getSource(), e );
                throw new PromotionException( "Path promotion rollback failed.", e );
            }
        }
    }

    private Future<PathsPromoteResult> submitRollbackPathsPromote( PathsPromoteResult result )
                    throws IndyWorkflowException
    {
        return detectOverload( () -> asyncPromotionService.submit( () -> {
            if ( result.getCompletedPaths().isEmpty() )
            {
                // clear errors so client don't misunderstand rollback result
                logger.info( "Nothing to rollback (completed empty), promotionId: {}",
                             result.getRequest().getPromotionId() );
                result.setError( null );
                result.setValidations( null );
                return result;
            }

            /*
             * Rollback is the opposite of promotion. We just revert the src and target, set the paths
             * to the completed, and call the doPathsPromotion.
             */
            PathsPromoteRequest request = result.getRequest();
            PathsPromoteRequest newRequest = new PathsPromoteRequest( request.getTarget(), request.getSource(),
                                                                      result.getCompletedPaths() );
            newRequest.setPurgeSource( true );

            PathsPromoteResult ret;
            try
            {
                ret = doPathsPromotion( newRequest, true, null );
            }
            catch ( Exception ex )
            {
                String msg = "Rollback path promotion failed. Target: " + request.getTarget() + ", Source: "
                                + request.getSource() + ", Reason: " + getStackTrace( ex );
                logger.warn( msg );
                ret = new PathsPromoteResult( request, msg );
            }

            if ( ret.succeeded() )
            {
                result.setPendingPaths( result.getCompletedPaths() );
                result.setCompletedPaths( null );
            }
            else
            {
                result.setError( ret.getError() );
            }

            if ( result.getRequest().getCallback() != null )
            {
                return callbackHelper.callback( result.getRequest().getCallback(), result );
            }

            return result;
        } ) );
    }

    private PathsPromoteResult doPathsPromotion( PathsPromoteRequest request, boolean skipValidation, String baseUrl )
                    throws IndyWorkflowException, PromotionValidationException
    {
        final Set<String> paths = request.getPaths();
        final StoreKey source = request.getSource();
        logger.info( "Do paths promotion, promotionId: {}, source: {}, target: {}, size: {}", request.getPromotionId(),
                     source, request.getTarget(), paths != null ? paths.size() : -1 );

        List<Transfer> contents;
        if ( paths == null || paths.isEmpty() )
        {
            // This is used to let galley ignore the NPMPathStorageCalculator handling,
            // which will append package.json to a directory transfer and make listing not applicable.
            ThreadContext context = ThreadContext.getContext( true );
            context.put( RequestContextHelper.IS_RAW_VIEW, Boolean.TRUE );
            contents = downloadManager.listRecursively( source, DownloadManager.ROOT_PATH );
            context.put( RequestContextHelper.IS_RAW_VIEW, Boolean.FALSE );
        }
        else
        {
            contents = promotionHelper.getTransfersForPaths( source, paths );
        }

        final Set<String> pending = contents.stream().map( Transfer::getPath ).collect( Collectors.toSet() );

        if ( pending.isEmpty() )
        {
            return new PathsPromoteResult( request, pending, emptySet(), emptySet(), null );
        }

        AtomicReference<Exception> ex = new AtomicReference<>();
        StoreKeyPaths plk = new StoreKeyPaths( request.getTargetKey(), pending );

        PathsPromoteResult promoteResult;

        if ( request.isFailWhenExists() )
        {
            promoteResult = conflictManager.checkAnd( plk, pathsLockKey -> runValidationAndPathPromotions( skipValidation, request, baseUrl, ex, pending, contents ), pathsLockKey -> {
                String msg = String.format( "Conflict detected, store: %s, paths: %s", pathsLockKey.getTarget(), pending );
                logger.warn( msg );
                return new PathsPromoteResult( request, pending, emptySet(), emptySet(), msg, null );
            } );
        }
        else
        {
            promoteResult = runValidationAndPathPromotions( skipValidation, request, baseUrl, ex, pending, contents );
        }

        if ( ex.get() != null )
        {
            throwProperException( ex.get() );
        }

        // purge only if all paths were promoted successfully
        if ( promoteResult != null && promoteResult.succeeded() && request.isPurgeSource() )
        {
            promotionHelper.purgeSourceQuietly( request.getSource(), pending );
        }

        return promoteResult;
    }

    private PathsPromoteResult runValidationAndPathPromotions( boolean skipValidation, PathsPromoteRequest request,
                                                               String baseUrl, AtomicReference<Exception> ex,
                                                               Set<String> pending, List<Transfer> contents )
    {
        ValidationResult validationResult = new ValidationResult();
        if ( !skipValidation )
        {
            try
            {
                ValidationRequest vr = validator.validate( request, validationResult, baseUrl );
            }
            catch ( Exception e )
            {
                ex.set( e );
                return null;
            }
        }

        if ( validationResult.isValid() )
        {
            if ( request.isDryRun() )
            {
                return new PathsPromoteResult( request, pending, emptySet(), emptySet(), validationResult );
            }
            PathsPromoteResult result = runPathPromotions( request, pending, contents, validationResult );
            if ( result.succeeded() )
            {
                promotionHelper.updatePathPromoteMetrics( contents.size(), result );
            }
            else
            {
                logger.info( "Path promotion failed. Result: " + result );
            }
            return result;
        }

        return new PathsPromoteResult( request, pending, emptySet(), emptySet(), validationResult );
    }

    private PathsPromoteResult runPathPromotions( final PathsPromoteRequest request, final Set<String> pending,
                                                  final List<Transfer> contents, final ValidationResult validation )
    {
        long begin = System.currentTimeMillis();

        PromotionHelper.PromotionRepoRetrievalResult checkResult = promotionHelper.checkAndRetrieveSourceAndTargetRepos( request );
        if ( checkResult.hasErrors() )
        {
            return new PathsPromoteResult( request, pending, emptySet(), emptySet(),
                                           StringUtils.join( checkResult.errors, "\n" ), validation );
        }

        final ArtifactStore targetStore = checkResult.targetStore;

        StoreKey targetKey = targetStore.getKey();
        logger.info( "Run promotion from: {} to: {}, paths: {}", request.getSource(), targetKey, pending );
        Set<Group> affectedGroups;
        try
        {
            affectedGroups = storeManager.query().getGroupsAffectedBy( targetKey );
            logger.info( "Calculate affected groups, target: {}, affected-groups: {}", targetKey, affectedGroups );
        }
        catch ( IndyDataException e )
        {
            logger.error( "Get affected groups failed", e );
            return new PathsPromoteResult( request, pending, emptySet(), emptySet(),
                                           "Get affected groups failed, " + e.getMessage(), validation );
        }

        DrainingExecutorCompletionService<Set<PathTransferResult>> svc =
                        new DrainingExecutorCompletionService<>( transferService );

        int corePoolSize = transferService.getCorePoolSize();
        int size = contents.size();
        int batchSize = getParalleledBatchSize( size, corePoolSize );
        logger.info( "Execute parallel on collection, size: {}, batch: {}", size, batchSize );
        Collection<Collection<Transfer>> batches = batch( contents, batchSize );

        final List<String> errors = new ArrayList<>();

        try
        {
            detectOverloadVoid( () -> batches.forEach(
                            batch -> svc.submit( newPathPromotionsJob( batch, targetStore, request, affectedGroups ) ) ) );
        }
        catch ( IndyWorkflowException e )
        {
            // might be PoolOverloadException. Log it and continue to revert any completed paths
            String msg = String.format( "Failed to submit all path promotion jobs. Error: %s", e.toString() );
            logger.error( msg, e );
            errors.add( msg );
        }

        final Set<PathTransferResult> results = new HashSet<>();

        try
        {
            svc.drain( results::addAll );
        }
        catch ( InterruptedException | ExecutionException e )
        {
            String msg = String.format( "Error waiting for promotion of: %s to: %s", request.getSource(),
                                        request.getTarget() );
            logger.error( msg, e );
            errors.add( msg );
        }

        final Set<String> completed = new HashSet<>();
        final Set<String> skipped = new HashSet<>();

        results.forEach( result -> {
            if ( result.error != null )
            {
                errors.add( result.error );
            }
            else if ( result.skipped )
            {
                skipped.add( result.path );
            }
            else
            {
                completed.add( result.path );
            }
        } );

        PathsPromoteResult result;
        if ( !errors.isEmpty() )
        {
            List<String> rollbackErrors = promotionHelper.deleteFromStore( completed, targetStore );
            errors.addAll( rollbackErrors );
            result = new PathsPromoteResult( request, pending, emptySet(), emptySet(), StringUtils.join( errors, "\n" ),
                                             validation );
        }
        else
        {
            result = new PathsPromoteResult( request, emptySet(), completed, skipped, null, validation );
            final String name = String.format( "PromoteNFCClean-method(%s)-source(%s)-target(%s)", "runPathPromotions",
                                               request.getSource(), targetStore.getKey() );
            final String context =
                    String.format( "Class: %s, method: %s, source: %s, target: %s", this.getClass().getName(),
                                   "runPathPromotions", request.getSource(), targetStore.getKey() );
            storeManager.asyncGroupAffectedBy( new StoreDataManager.ContextualTask( name, context,
                                                                                    () -> promotionHelper.clearStoreNFC(
                                                                                                    completed,
                                                                                                    targetStore,
                                                                                                    affectedGroups ) ) );
            if ( request.isFireEvents() )
            {
                fireEvent( promoteCompleteEvent, new PathsPromoteCompleteEvent( result ) );
            }
        }

        logger.info( "Promotion completed, promotionId: {}, timeInSeconds: {}", request.getPromotionId(),
                     timeInSeconds( begin ) );
        return result;
    }

    private Callable<Set<PathTransferResult>> newPathPromotionsJob( final Collection<Transfer> transfers,
                                                                    final ArtifactStore tgt,
                                                                    final PathsPromoteRequest request,
                                                                    final Set<Group> affectedGroups )
    {
        return () -> {
            Set<String> pathsForMDC = new HashSet<>();
            Set<PathTransferResult> results = new HashSet<>();
            for ( Transfer transfer : transfers )
            {
                pathsForMDC.add( transfer.getPath() );

                PathTransferResult ret = doPathTransfer( transfer, tgt, request, affectedGroups );
                results.add( ret );
            }
            RequestContextHelper.setContext( PROMOTION_CONTENT_PATH, pathsForMDC.toString() );
            return results;
        };
    }

    private PathTransferResult doPathTransfer( Transfer transfer, final ArtifactStore tgt,
                                               final PathsPromoteRequest request, final Set<Group> affectedGroups )
                    throws IndyWorkflowException
    {
        logger.debug( "Do path transfer, transfer: {}, target: {}", transfer, tgt );

        if ( transfer == null )
        {
            final String error = String.format( "Warning: doPathTransfer cannot process null transfer to target: %s", tgt );
            logger.error( error );
            //FIXME: throw IndyWorkflowException is better?
            PathTransferResult result = new PathTransferResult( "" );
            result.error = error;
            return result;
        }

        long begin = System.currentTimeMillis();

        final String path = transfer.getPath();
        PathTransferResult result = new PathTransferResult( path );

        if ( !transfer.exists() )
        {
            logger.debug( "Transfer not exist, {}", transfer );
            SpecialPathInfo pathInfo = specialPathManager.getSpecialPathInfo( transfer, tgt.getPackageType() );
            // if we can't decorate it, that's because we don't want to automatically generate checksums, etc. for it
            // i.e. it's something we would generate on demand for another file.
            if ( pathInfo != null && !pathInfo.isDecoratable() )
            {
                logger.info( "Skipping missing, not decoratable path: {}", transfer );
                result.skipped = true;
                return result;
            }

            if ( promotionHelper.isRemoteTransfer( transfer ) )
            {
                transfer = promotionHelper.redownload( transfer ); // try re-download it for remote artifacts
            }

            if ( transfer == null || !transfer.exists() )
            {
                String msg = String.format( "Failed to promote: %s. Source file not exists.", transfer );
                logger.info( msg );
                result.error = msg;
                return result;
            }
        }

        Transfer target = contentManager.getTransfer( tgt, path, UPLOAD );
        EventMetadata eventMetadata = new EventMetadata().set( IGNORE_READONLY, true );

        /*
         * if we hit an existing metadata.xml, we remove it from both target repo and affected groups. The metadata
         * will be regenerated on next request.
         */
        SpecialPathInfo pathInfo = specialPathManager.getSpecialPathInfo( target, tgt.getPackageType() );
        if ( pathInfo != null && pathInfo.isMetadata() )
        {
            try
            {
                if ( target != null && target.exists() )
                {
                    contentManager.delete( tgt, path, eventMetadata );
                }
                result.skipped = true;
                logger.info( "Metadata, mark as skipped and remove it if exists, target: {}", target );
            }
            catch ( IndyWorkflowException e )
            {
                String msg = String.format( "Failed to promote metadata: %s. Target: %s. Error: %s", transfer,
                                            request.getTarget(), e.getMessage() );
                logger.error( msg, e );
                result.error = msg;
            }

            return result;
        }

        if ( target != null && target.exists() )
        {
            /*
             * e.g., fail in case of promotion of built artifacts into pnc-builds while it should pass (skip them)
             * in case of promotion of dependencies into shared-imports.
             */
            if ( request.isFailWhenExists() )
            {
                String msg = String.format( "Failed to promote: %s. Target: %s. Target file already exists.",
                                            transfer, request.getTarget() );
                logger.info( msg );
                result.error = msg;
            }
            else
            {
                result.skipped = true;
            }
            return result;
        }

        logger.debug( "Store target transfer: {}", target );
        eventMetadata.set( AFFECTED_GROUPS, new ValuePipe<>( affectedGroups ) ).set( TARGET_STORE, tgt );

        try (InputStream stream = transfer.openInputStream( true ))
        {
            contentManager.store( tgt, path, stream, UPLOAD, eventMetadata );
        }
        catch ( final IOException e )
        {
            String msg = String.format( "Failed to promote: %s. Error: %s", transfer, e.getMessage() );
            result.error = msg;
            logger.error( msg, e );
        }

        logger.info( "Promotion transfer completed, target: {}, path: {}, timeInMillSeconds: {}", tgt.getKey(), path,
                     timeInMillSeconds( begin ) );

        return result;
    }

}
