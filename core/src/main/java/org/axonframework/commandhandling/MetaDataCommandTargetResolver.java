package org.axonframework.commandhandling;

import org.axonframework.common.Assert;

/**
 * CommandTargetResolver implementation that uses MetaData entries to extract the identifier and optionally the version
 * of the aggregate that the command targets.
 * <p/>
 * While this may require duplication of data (as the identifier is already included in the payload as well), it is a
 * more performing alternative to a reflection based CommandTargetResolvers.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaDataCommandTargetResolver implements CommandTargetResolver {

    private final String identifierKey;
    private final String versionKey;

    /**
     * Initializes the MetaDataCommandTargetResolver to use the given <code>identifierKey</code> as the MetaData
     * key to the aggregate identifier, and a <code>null</code> (ignored) version.
     * <p/>
     * When the given <code>identifierKey</code> is not present in a command's MetaData, {@link
     * #resolveTarget(CommandMessage)} will raise an {@link IllegalArgumentException}
     *
     * @param identifierKey The key of the meta data field containing the aggregate identifier
     */
    public MetaDataCommandTargetResolver(String identifierKey) {
        this(identifierKey, null);
    }

    /**
     * Initializes the MetaDataCommandTargetResolver to use the given <code>identifierKey</code> as the MetaData
     * key to the aggregate identifier, and the given <code>versionKey</code> as key to the (optional) version entry.
     * <p/>
     * When the given <code>identifierKey</code> is not present in a command's MetaData, {@link
     * #resolveTarget(CommandMessage)} will raise an {@link IllegalArgumentException}
     *
     * @param identifierKey The key of the meta data field containing the aggregate identifier
     * @param versionKey    The key of the meta data field containing the expected aggregate version. A
     *                      <code>null</code> value may be provided to ignore the version
     */
    public MetaDataCommandTargetResolver(String identifierKey, String versionKey) {
        this.versionKey = versionKey;
        this.identifierKey = identifierKey;
    }

    @Override
    public VersionedAggregateIdentifier resolveTarget(CommandMessage<?> command) {
        Object identifier = command.getMetaData().get(identifierKey);
        Assert.notNull(identifier, "The MetaData for the command does not exist or contains a null value");
        Long version = (Long) (versionKey == null ? null : command.getMetaData().get(versionKey));
        return new VersionedAggregateIdentifier(identifier, version);
    }
}
