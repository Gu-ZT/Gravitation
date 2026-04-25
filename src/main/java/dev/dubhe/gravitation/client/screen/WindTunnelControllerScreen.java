package dev.dubhe.gravitation.client.screen;

import dev.dubhe.gravitation.block.WindTunnelControllerBlock;
import dev.dubhe.gravitation.block.entity.WindTunnelControllerBlockEntity;
import dev.dubhe.gravitation.menu.WindTunnelControllerMenu;
import dev.dubhe.gravitation.network.payload.UpdateWindTunnelControllerPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.IntConsumer;
import javax.annotation.Nullable;

public class WindTunnelControllerScreen extends AbstractContainerScreen<WindTunnelControllerMenu> {
    private static final int WIDTH = 196;
    private static final int HEIGHT = 132;
    private static final int SLIDER_WIDTH = 120;
    private static final int FIELD_WIDTH = 42;
    private static final int CLIENT_EDIT_GRACE_TICKS = 8;
    private static final int LARGE_STEP = 5;
    private int targetLength;
    private int targetAirspeed;
    private boolean enabled;
    private boolean suppressUpdates;
    private boolean waitingForServerState;
    private int pendingSyncTicks;
    private int lastSentLength;
    private int lastSentAirspeed;
    private boolean lastSentEnabled;
    private @Nullable NumericSlider lengthSlider;
    private @Nullable NumericSlider airspeedSlider;
    private @Nullable NumericEditBox lengthField;
    private @Nullable NumericEditBox airspeedField;
    private @Nullable Button enabledButton;

    public WindTunnelControllerScreen(WindTunnelControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 10000;
        this.targetLength = 16;
        this.targetAirspeed = 12;
        this.lastSentLength = this.targetLength;
        this.lastSentAirspeed = this.targetAirspeed;
    }

    protected void init() {
        super.init();
        this.titleLabelX = 12;
        this.titleLabelY = 10;
        this.loadControllerState();
        int left = this.leftPos;
        int top = this.topPos;
        this.enabledButton = this.addRenderableWidget(Button.builder(
            Component.empty(), (button) -> {
                this.enabled = !this.enabled;
                this.updateEnabledButton();
                this.sendSettings();
            }
        ).bounds(left + 12, top + 28, 76, 20).build());
        this.lengthSlider = this.addRenderableWidget(new NumericSlider(
            left + 12,
            top + 58,
            SLIDER_WIDTH,
            Component.translatable("block.windtunnel.wind_tunnel_controller.target_length"),
            1,
            64,
            this.targetLength,
            (value) -> {
                this.targetLength = value;
                if (this.lengthField != null) {
                    this.lengthField.syncValue(value);
                }

            },
            this::sendSettings
        ));
        this.airspeedSlider = this.addRenderableWidget(new NumericSlider(
            left + 12,
            top + 92,
            SLIDER_WIDTH,
            Component.translatable("block.windtunnel.wind_tunnel_controller.target_airspeed"),
            0,
            64,
            this.targetAirspeed,
            (value) -> {
                this.targetAirspeed = value;
                if (this.airspeedField != null) {
                    this.airspeedField.syncValue(value);
                }

            },
            this::sendSettings
        ));
        this.lengthField = this.addRenderableWidget(new NumericEditBox(
            left + 142, top + 58, this.targetLength, 1, 64, (value) -> {
            this.targetLength = value;
            if (this.lengthSlider != null) {
                this.lengthSlider.setExternalValue(value);
            }

            this.sendSettings();
        }
        ));
        this.airspeedField = this.addRenderableWidget(new NumericEditBox(
            left + 142, top + 92, this.targetAirspeed, 0, 64, (value) -> {
            this.targetAirspeed = value;
            if (this.airspeedSlider != null) {
                this.airspeedSlider.setExternalValue(value);
            }

            this.sendSettings();
        }
        ));
        this.updateEnabledButton();
        this.lengthField.syncValue(this.targetLength);
        this.airspeedField.syncValue(this.targetAirspeed);
    }

    public void containerTick() {
        super.containerTick();
        if (this.pendingSyncTicks > 0) {
            --this.pendingSyncTicks;
        }

        this.loadControllerState();
    }

    public void onClose() {
        this.commitFocusedFields();
        super.onClose();
    }

    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, -1583169);
        guiGraphics.fill(
            this.leftPos + 1,
            this.topPos + 1,
            this.leftPos + this.imageWidth - 1,
            this.topPos + this.imageHeight - 1,
            -527896
        );
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + 1, -7705786);
        guiGraphics.fill(
            this.leftPos,
            this.topPos + this.imageHeight - 1,
            this.leftPos + this.imageWidth,
            this.topPos + this.imageHeight,
            -7705786
        );
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + 1, this.topPos + this.imageHeight, -7705786);
        guiGraphics.fill(
            this.leftPos + this.imageWidth - 1,
            this.topPos,
            this.leftPos + this.imageWidth,
            this.topPos + this.imageHeight,
            -7705786
        );
        guiGraphics.fill(this.leftPos + 8, this.topPos + 24, this.leftPos + this.imageWidth - 8, this.topPos + 25, -2570320);
        guiGraphics.fill(this.leftPos + 10, this.topPos + 52, this.leftPos + this.imageWidth - 10, this.topPos + 79, 866616666);
        guiGraphics.fill(this.leftPos + 10, this.topPos + 86, this.leftPos + this.imageWidth - 10, this.topPos + 113, 866616666);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
    }

    private void loadControllerState() {
        if (this.minecraft != null && this.minecraft.level != null) {
            if ((this.lengthSlider == null || !this.lengthSlider.isSliding()) && (this.airspeedSlider == null || !this.airspeedSlider.isSliding())) {
                BlockEntity var2 = this.minecraft.level.getBlockEntity(this.menu.getControllerPos());
                if (var2 instanceof WindTunnelControllerBlockEntity controller) {
                    boolean var6 = this.minecraft.level.getBlockState(this.menu.getControllerPos())
                        .getValue(
                            WindTunnelControllerBlock.ENABLED);
                    int blockLength = controller.getTargetLength();
                    int blockAirspeed = controller.getTargetAirspeed();
                    if (this.waitingForServerState) {
                        boolean serverCaughtUp = var6 == this.lastSentEnabled && blockLength == this.lastSentLength && blockAirspeed == this.lastSentAirspeed;
                        if (serverCaughtUp) {
                            this.waitingForServerState = false;
                            this.pendingSyncTicks = 0;
                        } else {
                            if (this.pendingSyncTicks > 0) {
                                return;
                            }

                            this.waitingForServerState = false;
                        }
                    }

                    if (var6 != this.enabled || blockLength != this.targetLength || blockAirspeed != this.targetAirspeed) {
                        this.suppressUpdates = true;
                        this.enabled = var6;
                        this.targetLength = blockLength;
                        this.targetAirspeed = blockAirspeed;
                        if (this.enabledButton != null) {
                            this.updateEnabledButton();
                        }

                        if (this.lengthSlider != null) {
                            this.lengthSlider.setExternalValue(this.targetLength);
                        }

                        if (this.airspeedSlider != null) {
                            this.airspeedSlider.setExternalValue(this.targetAirspeed);
                        }

                        if (this.lengthField != null) {
                            this.lengthField.syncValue(this.targetLength);
                        }

                        if (this.airspeedField != null) {
                            this.airspeedField.syncValue(this.targetAirspeed);
                        }

                        this.suppressUpdates = false;
                    }
                }
            }
        }
    }

    private void sendSettings() {
        if (!this.suppressUpdates) {
            this.waitingForServerState = true;
            this.pendingSyncTicks = 8;
            this.lastSentLength = this.targetLength;
            this.lastSentAirspeed = this.targetAirspeed;
            this.lastSentEnabled = this.enabled;
            PacketDistributor.sendToServer(
                new UpdateWindTunnelControllerPayload(
                    this.menu.getControllerPos(),
                    this.targetLength,
                    this.targetAirspeed,
                    this.enabled
                )
            );
        }
    }

    private void commitFocusedFields() {
        if (this.lengthField != null && this.lengthField.isFocused()) {
            this.lengthField.commitValue();
        }

        if (this.airspeedField != null && this.airspeedField.isFocused()) {
            this.airspeedField.commitValue();
        }

    }

    private void updateField(EditBox field, int value) {
        String text = Integer.toString(value);
        if (!text.equals(field.getValue())) {
            field.setValue(text);
        }

    }

    private void updateEnabledButton() {
        if (this.enabledButton != null) {
            this.enabledButton.setMessage(Component.translatable(this.enabled
                                                                 ? "block.windtunnel.wind_tunnel_controller.enabled"
                                                                 : "block.windtunnel.wind_tunnel_controller.disabled"));
        }
    }

    private static double normalize(int value, int minValue, int maxValue) {
        return (double) (value - minValue) / (double) (maxValue - minValue);
    }

    private static int denormalize(double value, int minValue, int maxValue) {
        return Mth.clamp((int) Math.round((double) minValue + value * (double) (maxValue - minValue)), minValue, maxValue);
    }

    private class NumericSlider extends AbstractSliderButton {
        private final Component label;
        private final int minValue;
        private final int maxValue;
        private final IntConsumer onPreviewChange;
        private final Runnable onCommit;
        private int currentValue;
        private int interactionStartValue;
        private boolean sliding;
        private boolean commitQueued;

        private NumericSlider(
            int x,
            int y,
            int width,
            Component label,
            int minValue,
            int maxValue,
            int initialValue,
            IntConsumer onPreviewChange,
            Runnable onCommit
        ) {
            super(x, y, width, 20, Component.empty(), WindTunnelControllerScreen.normalize(initialValue, minValue, maxValue));
            this.label = label;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.onPreviewChange = onPreviewChange;
            this.onCommit = onCommit;
            this.currentValue = initialValue;
            this.interactionStartValue = initialValue;
            this.updateMessage();
        }

        protected void updateMessage() {
            this.setMessage(Component.literal(Integer.toString(this.currentValue)));
        }

        protected void applyValue() {
            int newValue = WindTunnelControllerScreen.denormalize(this.value, this.minValue, this.maxValue);
            if (newValue != this.currentValue) {
                this.currentValue = newValue;
                this.updateMessage();
                this.onPreviewChange.accept(newValue);
                if (this.sliding) {
                    this.commitQueued = true;
                } else {
                    this.onCommit.run();
                }

            }
        }

        private void setExternalValue(int value) {
            this.currentValue = value;
            this.value = WindTunnelControllerScreen.normalize(value, this.minValue, this.maxValue);
            this.updateMessage();
        }

        private boolean isSliding() {
            return this.sliding;
        }

        public void onClick(double mouseX, double mouseY) {
            this.beginInteraction();
            super.onClick(mouseX, mouseY);
        }

        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (!this.sliding) {
                this.beginInteraction();
            }

            super.onDrag(mouseX, mouseY, dragX, dragY);
        }

        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            this.finishInteraction();
        }

        private void beginInteraction() {
            this.sliding = true;
            this.commitQueued = false;
            this.interactionStartValue = this.currentValue;
        }

        private void finishInteraction() {
            if (this.sliding) {
                this.sliding = false;
                if (this.commitQueued || this.currentValue != this.interactionStartValue) {
                    this.onCommit.run();
                }

                this.commitQueued = false;
            }
        }

        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawString(WindTunnelControllerScreen.this.font, this.label, this.getX(), this.getY() - 10, 4210752, false);
        }
    }

    private class NumericEditBox extends EditBox {
        private final int minValue;
        private final int maxValue;
        private final IntConsumer onApply;
        private int committedValue;

        private NumericEditBox(int x, int y, int initialValue, int minValue, int maxValue, IntConsumer onApply) {
            super(WindTunnelControllerScreen.this.font, x, y, FIELD_WIDTH, 20, Component.empty());
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.onApply = onApply;
            this.committedValue = initialValue;
            this.setMaxLength(3);
            this.setFilter((value) -> value.isEmpty() || value.matches("\\d{0,3}"));
            this.setValue(Integer.toString(initialValue));
        }

        public void setFocused(boolean focused) {
            boolean wasFocused = this.isFocused();
            super.setFocused(focused);
            if (wasFocused && !focused) {
                this.commitValue();
            }

        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.isFocused()) {
                if (keyCode == 257 || keyCode == 335) {
                    this.commitValue();
                    this.setFocused(false);
                    return true;
                }

                if (keyCode == 265 || keyCode == 262) {
                    this.stepValue(this.stepSize());
                    return true;
                }

                if (keyCode == 264 || keyCode == 263) {
                    this.stepValue(-this.stepSize());
                    return true;
                }
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private void syncValue(int value) {
            this.committedValue = value;
            if (!this.isFocused()) {
                WindTunnelControllerScreen.this.updateField(this, value);
            }

        }

        private void commitValue() {
            if (!WindTunnelControllerScreen.this.suppressUpdates) {
                if (this.getValue().isEmpty()) {
                    WindTunnelControllerScreen.this.updateField(this, this.committedValue);
                } else {
                    int clampedValue = Mth.clamp(Integer.parseInt(this.getValue()), this.minValue, this.maxValue);
                    boolean textChanged = !Integer.toString(clampedValue).equals(this.getValue());
                    boolean valueChanged = clampedValue != this.committedValue;
                    this.committedValue = clampedValue;
                    if (textChanged) {
                        WindTunnelControllerScreen.this.updateField(this, clampedValue);
                    }

                    if (valueChanged) {
                        this.onApply.accept(clampedValue);
                    }

                }
            }
        }

        private void stepValue(int delta) {
            int baseValue = this.getValue().isEmpty() ? this.committedValue : Integer.parseInt(this.getValue());
            int steppedValue = Mth.clamp(baseValue + delta, this.minValue, this.maxValue);
            this.committedValue = steppedValue;
            WindTunnelControllerScreen.this.updateField(this, steppedValue);
            this.onApply.accept(steppedValue);
        }

        private int stepSize() {
            return Screen.hasShiftDown() ? 5 : 1;
        }
    }
}
