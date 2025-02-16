package net.mehvahdjukaar.supplementaries.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.mehvahdjukaar.moonlight.api.misc.EventCalled;
import net.mehvahdjukaar.supplementaries.Supplementaries;
import net.mehvahdjukaar.supplementaries.common.items.SelectableContainerItem;
import net.mehvahdjukaar.supplementaries.common.network.ModNetwork;
import net.mehvahdjukaar.supplementaries.common.network.ServerBoundCycleSelectableContainerItemPacket;
import net.mehvahdjukaar.supplementaries.common.network.ServerBoundCycleSelectableContainerItemPacket.Slot;
import net.mehvahdjukaar.supplementaries.common.utils.IQuiverPlayer;
import net.mehvahdjukaar.supplementaries.common.utils.SlotReference;
import net.mehvahdjukaar.supplementaries.configs.ClientConfigs;
import net.mehvahdjukaar.supplementaries.reg.ClientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

public abstract class SelectableContainerItemHud {
    private static final ResourceLocation TEXTURE = Supplementaries.res("textures/gui/quiver_select.png");

    //behold states

    @Nullable
    private static SelectableContainerItem<?> itemUsed;
    private static Supplier<ItemStack> stackSlot;
    private static boolean usingKey = false; //false if just using
    private static double lastCumulativeMouseDx = 0;

    public static boolean isActive() {
        return itemUsed != null;
    }

    public static boolean isUsingKey() {
        return itemUsed != null && usingKey;
    }

    public static boolean isUsingItem() {
        return itemUsed != null && !usingKey;
    }

    //todo: test key and use combinaton
    public static void setUsingItem(SlotReference slot) {
        stackSlot = slot;
        if (slot.getItem() instanceof SelectableContainerItem<?> selectable) {
            itemUsed = selectable;
        } else {
            itemUsed = null;
        }
    }

    public static void setUsingKeybind(SlotReference slot) {
        setUsingItem(slot);
        usingKey = itemUsed != null;
    }

    private static void closeHud() {
        itemUsed = null;
        usingKey = false;
        stackSlot = SlotReference.EMPTY;
    }

    @EventCalled
    public static boolean onMouseScrolled(double scrollDelta) {
        if (itemUsed != null) {
            Player player = Minecraft.getInstance().player;
            int amount = scrollDelta > 0 ? -1 : 1;
            Slot slot = getUseItemSlot(player);
            sendCycle(amount, slot);
            return true;
        }
        return false;
    }

    @EventCalled
    public static void ohMouseMoved(double deltaX) {
        if (itemUsed != null && ClientConfigs.Items.QUIVER_MOUSE_MOVEMENT.get()) {

            double scale = Minecraft.getInstance().options.sensitivity().get() * 0.02;
            int oldI = (int) (lastCumulativeMouseDx * scale);
            lastCumulativeMouseDx += deltaX;
            int slotsMoved = (int) (lastCumulativeMouseDx * scale) - oldI;
            if (slotsMoved != 0) {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    Slot slot = getUseItemSlot(player);
                    sendCycle(slotsMoved, slot);
                }
            }
        }
    }

    private static void sendCycle(int slotsMoved, Slot slot) {
        var data = getItemUsedData();
        if (data != null) {
            ModNetwork.CHANNEL.sendToServer(new ServerBoundCycleSelectableContainerItemPacket(slotsMoved, slot, itemUsed));
            //update client immediately. stacks now may be desynced
            data.cycle(slotsMoved);
        }
    }

    private static void sendSetSlot(Slot slot, int number) {
        var data = getItemUsedData();
        if (data != null) {
            ModNetwork.CHANNEL.sendToServer(new ServerBoundCycleSelectableContainerItemPacket(
                    number, slot, true, itemUsed));
            getItemUsedData().setSelectedSlot(number);
        }
    }

    @EventCalled
    public static boolean onKeyPressed(int key, int action, int modifiers) {
        if (itemUsed == null) return false;
        if (action != GLFW.GLFW_PRESS) return false;

        Player player = Minecraft.getInstance().player;

        Slot slot = getUseItemSlot(player);
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                sendCycle(-1, slot);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                sendCycle(1, slot);
                return true;
            }
        }
        int number = key - 48;
        if (number >= 1 && number <= 9) {
            if (number <= itemUsed.getMaxSlots()) {
                sendSetSlot(slot, number - 1);
            }
            //cancels all number keys to prevent switching items
            return true;
        }
        return false;
    }


    public static void render(Minecraft minecraft, GuiGraphics graphics, float partialTicks, int screenWidth, int screenHeight) {
        if (itemUsed == null) return;
        if (!(minecraft.getCameraEntity() instanceof IQuiverPlayer)) {
            closeHud();
            return;
        }
        //checks for keypress here to handle all possible cases
        if (isUsingKey()) {
            if (!ClientRegistry.QUIVER_KEYBIND.isUnbound()) {
                boolean keyDown = InputConstants.isKeyDown(
                        Minecraft.getInstance().getWindow().getWindow(),
                        ClientRegistry.QUIVER_KEYBIND.key.getValue()
                );
                if (!keyDown) {
                    closeHud();
                    return;
                }
            }
        }

        var data = getItemUsedData();
        if (data == null) {
            closeHud();
            return;
        }
        ///gui.setupOverlayRenderState(true, false);
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        int selected = data.getSelectedSlot();
        List<ItemStack> items = data.getContentView();
        int slots = items.size();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int centerX = screenWidth / 2;

        poseStack.pushPose();
        poseStack.translate(0, 0, -90);

        int uWidth = slots * 20 + 2;
        int px = uWidth / 2;
        int py = screenHeight / 2 - 40;

        px += ClientConfigs.Items.QUIVER_GUI_X.get();
        py += ClientConfigs.Items.QUIVER_GUI_Y.get();

        graphics.blit(TEXTURE, centerX - px, py, 0, 0, uWidth - 1, 22);
        graphics.blit(TEXTURE, centerX + px - 1, py, 0, 0, 1, 22);
        graphics.blit(TEXTURE, centerX - px - 1 + selected * 20, py - 1, 24, 22, 24, 24);

        poseStack.popPose();

        int i1 = 1;

        for (int i = 0; i < slots; ++i) {
            int kx = centerX - px + 3 + i * 20;
            renderSlot(graphics, kx, py + 3, items.get(i), i1++, minecraft.font);
        }
        RenderSystem.disableBlend();


        ItemStack selectedArrow = items.get(selected);
        if (!selectedArrow.isEmpty()) {
            drawHighlight(minecraft, graphics, screenWidth, py, selectedArrow);
        }
        poseStack.popPose();
    }

    @Nullable
    private static SelectableContainerItem.AbstractData getItemUsedData() {
        if (itemUsed == null) return null;
        ItemStack stack = stackSlot.get();
        lastStack = stack;
        if (!stack.is(itemUsed)) return null;
        return itemUsed.getData(stack);
    }

    static ItemStack lastStack = ItemStack.EMPTY;

    private static void renderSlot(GuiGraphics graphics, int pX, int pY, ItemStack pStack, int seed, Font font) {
        if (!pStack.isEmpty()) {
            graphics.renderItem(pStack, pX, pY, seed);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            graphics.renderItemDecorations(font, pStack, pX, pY);
        }
    }


    @NotNull
    private static Slot getUseItemSlot(Player player) {
        return usingKey ? Slot.INVENTORY : (player.getUsedItemHand() == InteractionHand.MAIN_HAND ? Slot.MAIN_HAND : Slot.OFF_HAND);
    }


    @ExpectPlatform
    protected static void drawHighlight(Minecraft mc, GuiGraphics graphics, int screenWidth, int py, ItemStack selectedArrow) {
        throw new AssertionError();
    }


}