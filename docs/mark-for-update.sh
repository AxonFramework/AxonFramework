#!/bin/bash
# Script to add "Work In Progress" warnings to documentation files

set -e

DOCS_DIR="/home/abuijze/workspaces/axon/axon-5.0/docs/old-reference-guide"

WARNING_BLOCK='[WARNING]
====
🚧 **Axon 5 Update In Progress**

This documentation section is being updated for Axon Framework 5.
Some code examples may reference Axon 4 APIs.

For current Axon 5 APIs, see the link:../../../axon-5/api-changes.md[API Changes document].
===='

NOT_IMPLEMENTED_BLOCK='[WARNING]
====
⚠️ **Feature Under Development**

This feature is undergoing significant changes in Axon Framework 5.
Documentation will be updated when the implementation is complete.

Check the Axon 5.0 release notes for current status.
===='

add_warning() {
    local file=$1
    local warning_type=${2:-"wip"}  # wip or not-implemented

    if [ ! -f "$file" ]; then
        echo "File not found: $file"
        return 1
    fi

    # Check if warning already exists
    if grep -q "Axon 5 Update In Progress\|Feature Under Development" "$file"; then
        echo "Warning already exists in: $file"
        return 0
    fi

    # Find the first content line after the header (= Title)
    local temp_file=$(mktemp)
    local added=false
    local line_count=0

    while IFS= read -r line; do
        line_count=$((line_count + 1))
        echo "$line" >> "$temp_file"

        # Add warning after the title line (line starting with =)
        if ! $added && [[ $line =~ ^=.*$ ]] && [ $line_count -gt 1 ]; then
            echo "" >> "$temp_file"
            if [ "$warning_type" = "not-implemented" ]; then
                echo "$NOT_IMPLEMENTED_BLOCK" >> "$temp_file"
            else
                echo "$WARNING_BLOCK" >> "$temp_file"
            fi
            echo "" >> "$temp_file"
            added=true
        fi
    done < "$file"

    if $added; then
        mv "$temp_file" "$file"
        echo "✓ Added warning to: $file"
    else
        rm "$temp_file"
        echo "✗ Could not find suitable location in: $file"
        return 1
    fi
}

# Mark high-priority files that need updates
mark_wip_files() {
    echo "Marking WIP files..."

    # Entity modeling files
    add_warning "$DOCS_DIR/modules/axon-framework-commands/pages/modeling/aggregate.adoc" "wip"
    add_warning "$DOCS_DIR/modules/axon-framework-commands/pages/modeling/multi-entity-aggregates.adoc" "wip"
    add_warning "$DOCS_DIR/modules/axon-framework-commands/pages/modeling/state-stored-aggregates.adoc" "wip"
    add_warning "$DOCS_DIR/modules/axon-framework-commands/pages/modeling/aggregate-polymorphism.adoc" "wip"

    # Event processors
    add_warning "$DOCS_DIR/modules/events/pages/event-processors/index.adoc" "wip"
    add_warning "$DOCS_DIR/modules/events/pages/event-processors/dead-letter-queue.adoc" "wip"

    # Infrastructure
    add_warning "$DOCS_DIR/modules/axon-framework-commands/pages/infrastructure.adoc" "wip"
    add_warning "$DOCS_DIR/modules/events/pages/infrastructure.adoc" "wip"

    # Configuration
    add_warning "$DOCS_DIR/modules/axon-framework-commands/pages/configuration.adoc" "wip"

    # Tuning
    add_warning "$DOCS_DIR/modules/tuning/pages/event-processing.adoc" "wip"
    add_warning "$DOCS_DIR/modules/tuning/pages/event-snapshots.adoc" "wip"
}

# Mark files for potential removal (not implemented yet)
mark_not_implemented_files() {
    echo "Marking not-yet-implemented files..."

    # This one may not work with DCB
    add_warning "$DOCS_DIR/modules/axon-framework-commands/pages/modeling/aggregate-creation-from-another-aggregate.adoc" "not-implemented"
}

# Show usage
usage() {
    echo "Usage: $0 [wip|not-implemented|all]"
    echo ""
    echo "Options:"
    echo "  wip              - Mark files that need updating with WIP warning"
    echo "  not-implemented  - Mark files for features not yet implemented"
    echo "  all              - Mark all files"
    echo ""
    echo "Example:"
    echo "  $0 wip"
}

# Main
case "${1:-}" in
    wip)
        mark_wip_files
        ;;
    not-implemented)
        mark_not_implemented_files
        ;;
    all)
        mark_wip_files
        mark_not_implemented_files
        ;;
    --help|-h|help)
        usage
        ;;
    *)
        echo "Error: Invalid option"
        echo ""
        usage
        exit 1
        ;;
esac

echo ""
echo "Done! Review the changes with: git diff"
