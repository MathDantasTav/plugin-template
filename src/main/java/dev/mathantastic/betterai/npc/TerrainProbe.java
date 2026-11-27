package dev.mathantastic.betterai.npc;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.collision.WorldUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Block lookups for flying NPCs: where the ground is (tree tops count as ground) and whether it's a tree.
 */
final class TerrainProbe {

    static final int NO_GROUND = Integer.MIN_VALUE;

    private TerrainProbe() {
    }

    static ChunkStore chunkStore(ComponentAccessor<EntityStore> accessor) {
        return accessor.getExternalData().getWorld().getChunkStore();
    }

    // Y of the first free block above the ground at x/z, scanning down from top to bottom.
    static int groundLevel(ChunkStore chunks, double x, double z, int top, int bottom) {
        int bx = MathUtil.floor(x);
        int bz = MathUtil.floor(z);
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(bx, top, bz);
        if (section == null || !section.isValid()) {
            return NO_GROUND; // not loaded
        }
        int level = WorldUtil.findFarthestEmptySpaceBelow(chunks, bx, top, bz, bottom, bottom);
        return level <= bottom ? NO_GROUND : level;
    }

    static boolean isLeaves(ChunkStore chunks, double x, int y, double z) {
        int bx = MathUtil.floor(x);
        int bz = MathUtil.floor(z);
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(bx, y, bz);
        if (section == null || !section.isValid()) {
            return false;
        }
        Store<ChunkStore> store = chunks.getStore();
        BlockSection blocks = store.getComponent(section, BlockSection.getComponentType());
        if (blocks == null) {
            return false;
        }
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(bx, y, bz));
        return type != null && type.getId() != null && type.getId().contains("Leaves");
    }
}
