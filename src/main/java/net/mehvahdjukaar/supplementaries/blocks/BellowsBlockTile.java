package net.mehvahdjukaar.supplementaries.blocks;

import net.mehvahdjukaar.supplementaries.common.CommonUtil;
import net.mehvahdjukaar.supplementaries.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.*;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

public class BellowsBlockTile extends TileEntity implements ITickableTileEntity {
    private static final int RANGE = 5;
    private static final float PERIOD = 78;

    public float height = 0;
    public float prevHeight = 0;
    public int counter = 0;
    public BellowsBlockTile() {
        super(Registry.BELLOWS_TILE.get());
    }


    public AxisAlignedBB getBoundingBox(BlockState state) {
        return this.getBoundingBox(this.getDirection());
    }

    public AxisAlignedBB getBoundingBox(Direction direction) {
        float f = this.height;
        Direction.Axis axis = direction.getAxis();
        return axis == Direction.Axis.Y ? VoxelShapes.fullCube().getBoundingBox().grow(0,0, f) :
                VoxelShapes.fullCube().getBoundingBox().grow(0,f,0);
    }

    private AxisAlignedBB getTopBoundingBox(Direction directionIn) {
        Direction direction = directionIn.getOpposite();
        float f = this.height;
        return VoxelShapes.fullCube().getBoundingBox().contract(0,-f,0).contract((1+f)*direction.getXOffset(),
                (1+f)*direction.getYOffset(),(1+f)*direction.getZOffset());
    }

    //TODO: fix this.
    private void moveCollidedEntities() {
        boolean axis = this.getDirection().getAxis() == Direction.Axis.Y;
        Direction direction = axis ? Direction.NORTH : Direction.UP;
        for(int j=0; j<2; j++) {

            AxisAlignedBB axisalignedbb = this.getTopBoundingBox(direction).offset(this.pos);
            List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(null, axisalignedbb);

            if (!list.isEmpty()) {
                for (Entity entity : list) {
                    if (entity.getPushReaction() != PushReaction.IGNORE) {
                        double d0 = 0.0D;
                        double d1 = 0.0D;
                        double d2 = 0.0D;
                        AxisAlignedBB axisalignedbb1 = entity.getBoundingBox();
                        switch (direction.getAxis()) {
                            case X:
                                if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                                    d0 = axisalignedbb.maxX - axisalignedbb1.minX;
                                } else {
                                    d0 = axisalignedbb1.maxX - axisalignedbb.minX;
                                }

                                d0 = d0 + 0.01D;
                                break;
                            case Y:
                                if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                                    d1 = axisalignedbb.maxY - axisalignedbb1.minY;
                                } else {
                                    d1 = axisalignedbb1.maxY - axisalignedbb.minY;
                                }

                                d1 = d1 + 0.01D;
                                break;
                            case Z:
                                if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                                    d2 = axisalignedbb.maxZ - axisalignedbb1.minZ;
                                } else {
                                    d2 = axisalignedbb1.maxZ - axisalignedbb.minZ;
                                }

                                d2 = d2 + 0.01D;
                        }


                        entity.move(MoverType.SHULKER_BOX, new Vector3d(d0 * (double) direction.getXOffset(), d1 * (double) direction.getYOffset(), d2 * (double) direction.getZOffset()));
                    }
                }
            }
            direction = direction.getOpposite();
        }
    }


    public void tick() {
        int power = this.getBlockState().get(BellowsBlock.POWER);
        this.prevHeight = this.height;

        if(power!=0){
            this.counter++;


            float period = PERIOD - (power-1)*3;

            Direction facing = this.getDirection();

            //slope of animation. for particles and pusing entities
            float j = MathHelper.sin((float)Math.PI*2* this.counter / period);

            //client
            if (this.world.isRemote && this.world.rand.nextInt(2) == 0 &&
                    this.world.rand.nextFloat() < j &&
                    ! Block.hasEnoughSolidSide(this.world, this.pos.offset(facing), facing.getOpposite()))
                this.spawnParticles(this.world, this.pos);


            final float dh = 1 / 16f;//0.09375f;
            this.height = dh * MathHelper.cos((float)Math.PI*2* this.counter / period) - dh;



            //server
            if (!this.world.isRemote) {



                //push entities (only if pushing air)
                float g = this.counter%period;
                if ( g< 0.5f*period) {
                    List<Entity> list = this.world.getEntitiesWithinAABB(Entity.class,
                            CommonUtil.getDirectionBB(this.pos, facing, RANGE));

                    for (Entity entity : list) {

                        if (!this.inLineOfSight(entity, facing)) continue;

                        double velocity = 1/period; // Affects acceleration
                        double maxVelocity = 2; // Affects max speed

                        if (facing == Direction.UP) {
                            maxVelocity *= 0.5D;
                        }

                        double dist = entity.getDistanceSq(this.pos.getX()+0.5*facing.getXOffset(),
                                this.pos.getY()+0.5*facing.getYOffset(),this.pos.getZ()+0.5*facing.getZOffset());
                        velocity = velocity*(-MathHelper.sqrt(dist) + RANGE);

                        if (Math.abs(entity.getMotion().getCoordinate(facing.getAxis())) < maxVelocity) {
                            entity.setMotion(entity.getMotion().add(facing.getXOffset() * velocity, facing.getYOffset() * velocity, facing.getZOffset() * velocity));
                            entity.velocityChanged = true;
                        }
                    }
                }







                BlockPos frontpos = this.pos.offset(facing);

                //speeds up furnaces
                if(this.counter % 9- (power/2) == 0) {
                    TileEntity te = world.getTileEntity(frontpos);
                    if (te instanceof AbstractFurnaceTileEntity || te instanceof CampfireTileEntity) {
                        ((ITickableTileEntity) te).tick();
                    }
                }
                //refresh fire blocks
                //update more frequently block closed to it
                //fire updates (previous random tick) at a minimum of 30 ticks
                int n = 0;
                for (int a = 0; a <= RANGE; a++) {
                    if (this.counter % (15 * (a + 1)) != 0) {
                        n = a;
                        break;
                    }
                }
                //only first 4 block will ultimately be kept active. this could change with random ticks if unlucky
                for (int i = 0; i < n; i++) {
                    BlockState fb = this.world.getBlockState(frontpos);
                    if (fb.getBlock() instanceof FireBlock) {
                        int age = fb.get(FireBlock.AGE);
                        if (age != 0) {
                            world.setBlockState(frontpos, fb.with(FireBlock.AGE,
                                    MathHelper.clamp(age - 7, 0, 15)), 4);
                        }
                    }
                    frontpos = frontpos.offset(facing);
                }


            }
        }
        //resets counter when powered off
        else{
            this.counter=0;
            //closing animation only client side
            if(this.world.isRemote && this.height < 0)
                this.height = Math.min(this.height + 0.01f, 0);
        }
        if(this.height !=0){
            this.moveCollidedEntities();
        }
    }

    public boolean inLineOfSight(Entity entity, Direction facing) {
        int x = facing.getXOffset() * (MathHelper.floor(entity.getPosX()) - this.pos.getX());
        int y = facing.getYOffset() * (MathHelper.floor(entity.getPosY()) - this.pos.getY());
        int z = facing.getZOffset() * (MathHelper.floor(entity.getPosZ()) - this.pos.getZ());
        boolean flag = true;

        for(int i = 1; i < Math.abs(x + y + z); i++) {

            if(Block.hasEnoughSolidSide(this.world, this.pos.offset(facing, i), facing.getOpposite())) {
                flag = false;
            }
        }
        return flag;
    }

    @OnlyIn(Dist.CLIENT)
    public  void spawnParticles(World world, BlockPos pos) {
        Direction dir = this.getDirection();
        double xo = dir.getXOffset();
        double yo = dir.getYOffset();
        double zo = dir.getZOffset();
        double x = xo*0.5 + pos.getX() + 0.5 + (world.rand.nextFloat() - 0.5)/3d;
        double y = yo*0.5 + pos.getY() + 0.5 + (world.rand.nextFloat() - 0.5)/3d;
        double z = zo*0.5 + pos.getZ() + 0.5 + (world.rand.nextFloat() - 0.5)/3d;

        double vel = 0.125F + world.rand.nextFloat() * 0.2F;

        double velX = xo * vel;
        double velY = yo * vel;
        double velZ = zo * vel;

        world.addParticle(ParticleTypes.SMOKE, x, y, z, velX, velY, velZ);
    }

    public Direction getDirection() {
        return this.getBlockState().get(BellowsBlock.FACING);
    }

    @Override
    public void read(BlockState state, CompoundNBT compound) {
        super.read(state, compound);
        this.counter = compound.getInt("Progress");
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        super.write(compound);
        compound.putInt("Progress", this.counter);
        return compound;
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(this.pos, 0, this.getUpdateTag());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return this.write(new CompoundNBT());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        this.read(this.getBlockState(), pkt.getNbtCompound());
    }
}