package dev.dubhe.gravitation.client.screen;

import dev.dubhe.gravitation.block.entity.WindTunnelMountBlockEntity;
import dev.dubhe.gravitation.menu.WindTunnelMountMenu;
import dev.dubhe.gravitation.network.payload.UpdateWindTunnelMountPayload;
import dev.dubhe.gravitation.windtunnel.WindTunnelMountMeasurement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class WindTunnelMountScreen extends AbstractContainerScreen<WindTunnelMountMenu> {
    private static final int WIDTH = 248;
    private static final int HEIGHT = 264;
    private static final int SLIDER_WIDTH = 144;
    private static final int FIELD_WIDTH = 54;
    private static final int CLIENT_EDIT_GRACE_TICKS = 8;
    private static final int LABEL_COLOR = 4210752;
    private static final int PANEL_COLOR = -593950;
    private static final int BORDER_COLOR = -7904710;
    private static final int ROW_FILL = 866616666;
    private static final int SECTION_LINE = -2570320;
    private static final int BUTTON_ROW_Y = 26;
    private static final int FLOW_ROW_Y = 58;
    private static final int MEASUREMENT_TOP = 86;
    private static final int FIRST_SLIDER_Y = 128;
    private static final int SLIDER_SPACING = 26;
    private boolean suppressUpdates;
    private boolean locked;
    private Direction flowDirection;
    private double angleOfAttack;
    private double sideslipAngle;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private WindTunnelMountMeasurement measurement;
    private boolean waitingForServerState;
    private int pendingSyncTicks;
    private boolean lastSentLocked;
    private Direction lastSentFlowDirection;
    private double lastSentAngleOfAttack;
    private double lastSentSideslipAngle;
    private double lastSentOffsetX;
    private double lastSentOffsetY;
    private double lastSentOffsetZ;
    private @Nullable Button lockButton;
    private @Nullable CycleButton<Direction> flowDirectionButton;
    private @Nullable DecimalSlider angleSlider;
    private @Nullable DecimalSlider sideslipSlider;
    private @Nullable DecimalSlider offsetXSlider;
    private @Nullable DecimalSlider offsetYSlider;
    private @Nullable DecimalSlider offsetZSlider;
    private @Nullable DecimalEditBox angleField;
    private @Nullable DecimalEditBox sideslipField;
    private @Nullable DecimalEditBox offsetXField;
    private @Nullable DecimalEditBox offsetYField;
    private @Nullable DecimalEditBox offsetZField;

    public WindTunnelMountScreen(WindTunnelMountMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.flowDirection = Direction.NORTH;
        this.measurement = WindTunnelMountMeasurement.EMPTY;
        this.lastSentFlowDirection = Direction.NORTH;
        this.imageWidth = 248;
        this.imageHeight = 264;
        this.inventoryLabelY = 10000;
    }

    protected void init() {
        super.init();
        this.titleLabelX = 12;
        this.titleLabelY = 10;
        this.loadState();
        int left = this.leftPos;
        int top = this.topPos;
        this.lockButton = this.addRenderableWidget(Button.builder(
            Component.empty(), (button) -> {
                this.locked = !this.locked;
                this.updateLockButton();
                this.sendSettings(false);
            }
        ).bounds(left + 12, top + 26, 72, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("block.windtunnel.wind_tunnel_mount.clear_binding"),
                (button) -> this.sendSettings(true)
            )
            .bounds(left + 92, top + 26, 92, 20)
            .build());
        this.flowDirectionButton = this.addRenderableWidget(CycleButton.<Direction>builder((direction) -> Component.translatable(direction.getName()))
            .withValues(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN)
            .withInitialValue(this.flowDirection)
            .create(
                left + 12,
                top + 58,
                224,
                20,
                Component.translatable("block.windtunnel.wind_tunnel_mount.flow_direction"),
                (button, value) -> {
                    this.flowDirection = value;
                    if (!this.suppressUpdates) {
                        this.sendSettings(false);
                    }

                }
            ));
        this.angleSlider = this.addSlider(
            left,
            top,
            128,
            Component.translatable("block.windtunnel.wind_tunnel_mount.angle_of_attack"),
            -90.0F,
            90.0F,
            () -> this.angleOfAttack,
            (value) -> this.angleOfAttack = value,
            () -> this.syncField(this.angleField, this.angleOfAttack)
        );
        this.sideslipSlider = this.addSlider(
            left,
            top,
            154,
            Component.translatable("block.windtunnel.wind_tunnel_mount.sideslip_angle"),
            -90.0F,
            90.0F,
            () -> this.sideslipAngle,
            (value) -> this.sideslipAngle = value,
            () -> this.syncField(this.sideslipField, this.sideslipAngle)
        );
        this.offsetXSlider = this.addSlider(
            left,
            top,
            180,
            Component.translatable("block.windtunnel.wind_tunnel_mount.offset_x"),
            -64.0F,
            64.0F,
            () -> this.offsetX,
            (value) -> this.offsetX = value,
            () -> this.syncField(this.offsetXField, this.offsetX)
        );
        this.offsetYSlider = this.addSlider(
            left,
            top,
            206,
            Component.translatable("block.windtunnel.wind_tunnel_mount.offset_y"),
            -64.0F,
            64.0F,
            () -> this.offsetY,
            (value) -> this.offsetY = value,
            () -> this.syncField(this.offsetYField, this.offsetY)
        );
        this.offsetZSlider = this.addSlider(
            left,
            top,
            232,
            Component.translatable("block.windtunnel.wind_tunnel_mount.offset_z"),
            -64.0F,
            64.0F,
            () -> this.offsetZ,
            (value) -> this.offsetZ = value,
            () -> this.syncField(this.offsetZField, this.offsetZ)
        );
        this.angleField = this.addField(
            left + 182, top + 128, () -> this.angleOfAttack, (value) -> {
                this.angleOfAttack = value;
                this.angleSlider.setExternalValue(value);
                this.sendSettings(false);
            }, -90.0F, 90.0F
        );
        this.sideslipField = this.addField(
            left + 182, top + 128 + 26, () -> this.sideslipAngle, (value) -> {
                this.sideslipAngle = value;
                this.sideslipSlider.setExternalValue(value);
                this.sendSettings(false);
            }, -90.0F, 90.0F
        );
        this.offsetXField = this.addField(
            left + 182, top + 128 + 52, () -> this.offsetX, (value) -> {
                this.offsetX = value;
                this.offsetXSlider.setExternalValue(value);
                this.sendSettings(false);
            }, -64.0F, 64.0F
        );
        this.offsetYField = this.addField(
            left + 182, top + 128 + 78, () -> this.offsetY, (value) -> {
                this.offsetY = value;
                this.offsetYSlider.setExternalValue(value);
                this.sendSettings(false);
            }, -64.0F, 64.0F
        );
        this.offsetZField = this.addField(
            left + 182, top + 128 + 104, () -> this.offsetZ, (value) -> {
                this.offsetZ = value;
                this.offsetZSlider.setExternalValue(value);
                this.sendSettings(false);
            }, -64.0F, 64.0F
        );
        this.updateLockButton();
        this.syncField(this.angleField, this.angleOfAttack);
        this.syncField(this.sideslipField, this.sideslipAngle);
        this.syncField(this.offsetXField, this.offsetX);
        this.syncField(this.offsetYField, this.offsetY);
        this.syncField(this.offsetZField, this.offsetZ);
    }

    public void containerTick() {
        super.containerTick();
        if (this.pendingSyncTicks > 0) {
            --this.pendingSyncTicks;
        }

        this.loadState();
    }

    public void onClose() {
        this.commitFocusedFields();
        super.onClose();
    }

    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, -7904710);
        guiGraphics.fill(
            this.leftPos + 1,
            this.topPos + 1,
            this.leftPos + this.imageWidth - 1,
            this.topPos + this.imageHeight - 1,
            -593950
        );
        guiGraphics.fill(this.leftPos + 8, this.topPos + 48, this.leftPos + this.imageWidth - 8, this.topPos + 49, -2570320);
        guiGraphics.fill(this.leftPos + 8, this.topPos + 86 - 4, this.leftPos + this.imageWidth - 8, this.topPos + 86 + 26, 866616666);
        guiGraphics.fill(this.leftPos + 8, this.topPos + 86 + 34, this.leftPos + this.imageWidth - 8, this.topPos + 86 + 35, -2570320);

        for (int i = 0; i < 5; ++i) {
            int rowTop = this.topPos + 128 + i * 26 - 4;
            guiGraphics.fill(this.leftPos + 8, rowTop, this.leftPos + this.imageWidth - 8, rowTop + 24, 866616666);
        }

    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        guiGraphics.drawString(
            this.font,
            Component.translatable("block.windtunnel.wind_tunnel_mount.flow_direction"),
            12,
            50,
            4210752,
            false
        );
        guiGraphics.drawString(this.font, Component.translatable("block.windtunnel.wind_tunnel_mount.measurement"), 12, 86, 4210752, false);
        guiGraphics.drawString(
            this.font,
            Component.literal(String.format(
                Locale.ROOT,
                "L %.2f  D %.2f  Y %.2f",
                this.measurement.lift(),
                this.measurement.drag(),
                this.measurement.sideForce()
            )),
            12,
            98,
            4210752,
            false
        );
        guiGraphics.drawString(
            this.font, Component.literal(String.format(
                Locale.ROOT,
                "P %.2f  R %.2f  N %.2f",
                this.measurement.pitchMoment(),
                this.measurement.rollMoment(),
                this.measurement.yawMoment()
            )), 12, 110, 4210752, false
        );
    }

    private DecimalSlider addSlider(
        int left,
        int top,
        int y,
        Component label,
        double min,
        double max,
        Supplier<Double> getter,
        DoubleConsumer setter,
        Runnable syncField
    ) {
        DecimalSlider slider = new DecimalSlider(
            left + 12, top + y, 144, label, min, max, getter.get(), (value) -> {
            setter.accept(value);
            syncField.run();
        }, () -> this.sendSettings(false)
        );
        this.addRenderableWidget(slider);
        return slider;
    }

    private DecimalEditBox addField(int x, int y, Supplier<Double> getter, DoubleConsumer onApply, double min, double max) {
        DecimalEditBox field = new DecimalEditBox(this.font, x, y, 54, 20, getter.get(), min, max, onApply);
        this.addRenderableWidget(field);
        return field;
    }

    private void loadState() {
        Minecraft minecraft = this.minecraft;
        if (minecraft != null && minecraft.level != null) {
            BlockEntity var3 = minecraft.level.getBlockEntity(this.menu.getMountPos());
            if (var3 instanceof WindTunnelMountBlockEntity mount) {
                if (
                    (
                        this.angleSlider == null
                        || !this.angleSlider.isSliding()
                    )
                    && (
                        this.sideslipSlider == null
                        || !this.sideslipSlider.isSliding()
                    )
                    && (
                        this.offsetXSlider == null
                        || !this.offsetXSlider.isSliding()
                    )
                    && (
                        this.offsetYSlider == null
                        || !this.offsetYSlider.isSliding()
                    )
                    && (
                        this.offsetZSlider == null
                        || !this.offsetZSlider.isSliding()
                    )
                ) {
                    boolean blockLocked = mount.isLocked();
                    Direction blockFlowDirection = mount.getFlowDirection();
                    double blockAngleOfAttack = mount.getAngleOfAttack();
                    double blockSideslipAngle = mount.getSideslipAngle();
                    double blockOffsetX = mount.getOffsetX();
                    double blockOffsetY = mount.getOffsetY();
                    double blockOffsetZ = mount.getOffsetZ();
                    this.measurement = mount.getMeasurement();
                    if (this.waitingForServerState) {
                        @SuppressWarnings("SuspiciousNameCombination")
                        boolean serverCaughtUp = blockLocked == this.lastSentLocked
                                                 && blockFlowDirection == this.lastSentFlowDirection
                                                 && nearlyEquals(blockAngleOfAttack, this.lastSentAngleOfAttack)
                                                 && nearlyEquals(blockSideslipAngle, this.lastSentSideslipAngle)
                                                 && nearlyEquals(blockOffsetX, this.lastSentOffsetX)
                                                 && nearlyEquals(blockOffsetY, this.lastSentOffsetY)
                                                 && nearlyEquals(blockOffsetZ, this.lastSentOffsetZ);
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

                    this.suppressUpdates = true;
                    this.locked = blockLocked;
                    this.flowDirection = blockFlowDirection;
                    this.angleOfAttack = blockAngleOfAttack;
                    this.sideslipAngle = blockSideslipAngle;
                    this.offsetX = blockOffsetX;
                    this.offsetY = blockOffsetY;
                    this.offsetZ = blockOffsetZ;
                    if (this.lockButton != null) {
                        this.updateLockButton();
                    }

                    if (this.flowDirectionButton != null && this.flowDirectionButton.getValue() != this.flowDirection) {
                        this.flowDirectionButton.setValue(this.flowDirection);
                    }

                    this.syncSliderAndField(this.angleSlider, this.angleField, this.angleOfAttack);
                    this.syncSliderAndField(this.sideslipSlider, this.sideslipField, this.sideslipAngle);
                    this.syncSliderAndField(this.offsetXSlider, this.offsetXField, this.offsetX);
                    this.syncSliderAndField(this.offsetYSlider, this.offsetYField, this.offsetY);
                    this.syncSliderAndField(this.offsetZSlider, this.offsetZField, this.offsetZ);
                    this.suppressUpdates = false;
                } else {
                    this.measurement = mount.getMeasurement();
                }
            }
        }
    }

    private void syncSliderAndField(@Nullable DecimalSlider slider, @Nullable DecimalEditBox field, double value) {
        if (slider != null && !slider.isSliding()) {
            slider.setExternalValue(value);
        }

        if (field != null && !field.isFocused()) {
            this.syncField(field, value);
        }

    }

    private void syncField(@Nullable EditBox field, double value) {
        String text = formatValue(value);
        if (field != null && !text.equals(field.getValue())) {
            field.setValue(text);
        }
    }

    private void updateLockButton() {
        if (this.lockButton != null) {
            this.lockButton.setMessage(Component.translatable(this.locked
                                                              ? "block.windtunnel.wind_tunnel_mount.locked"
                                                              : "block.windtunnel.wind_tunnel_mount.unlocked"));
        }

    }

    private void sendSettings(boolean clearBinding) {
        if (!this.suppressUpdates) {
            if (!clearBinding) {
                this.waitingForServerState = true;
                this.pendingSyncTicks = 8;
                this.lastSentLocked = this.locked;
                this.lastSentFlowDirection = this.flowDirection;
                this.lastSentAngleOfAttack = this.angleOfAttack;
                this.lastSentSideslipAngle = this.sideslipAngle;
                this.lastSentOffsetX = this.offsetX;
                this.lastSentOffsetY = this.offsetY;
                this.lastSentOffsetZ = this.offsetZ;
            }

            PacketDistributor.sendToServer(
                new UpdateWindTunnelMountPayload(
                    this.menu.getMountPos(),
                    this.locked,
                    this.flowDirection,
                    this.angleOfAttack,
                    this.sideslipAngle,
                    this.offsetX,
                    this.offsetY,
                    this.offsetZ,
                    clearBinding
                )
            );
        }
    }

    private static String formatValue(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void commitFocusedFields() {
        if (this.angleField != null && this.angleField.isFocused()) {
            this.angleField.commitValue();
        }

        if (this.sideslipField != null && this.sideslipField.isFocused()) {
            this.sideslipField.commitValue();
        }

        if (this.offsetXField != null && this.offsetXField.isFocused()) {
            this.offsetXField.commitValue();
        }

        if (this.offsetYField != null && this.offsetYField.isFocused()) {
            this.offsetYField.commitValue();
        }

        if (this.offsetZField != null && this.offsetZField.isFocused()) {
            this.offsetZField.commitValue();
        }

    }

    private static boolean nearlyEquals(double left, double right) {
        return Math.abs(left - right) <= 1.0E-4;
    }

    private static double normalize(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    private static double denormalize(double value, double min, double max) {
        double scaled = min + value * (max - min);
        return (double) Math.round(scaled * (double) 100.0F) / (double) 100.0F;
    }

    private class DecimalSlider extends AbstractSliderButton {
        private final Component label;
        private final double minValue;
        private final double maxValue;
        private final DoubleConsumer onPreviewChange;
        private final Runnable onCommit;
        private double currentValue;
        private double interactionStartValue;
        private boolean sliding;
        private boolean commitQueued;

        private DecimalSlider(
            int x,
            int y,
            int width,
            Component label,
            double minValue,
            double maxValue,
            double initialValue,
            DoubleConsumer onPreviewChange,
            Runnable onCommit
        ) {
            super(x, y, width, 20, Component.empty(), WindTunnelMountScreen.normalize(initialValue, minValue, maxValue));
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
            this.setMessage(Component.literal(WindTunnelMountScreen.formatValue(this.currentValue)));
        }

        protected void applyValue() {
            double newValue = WindTunnelMountScreen.denormalize(this.value, this.minValue, this.maxValue);
            if (!(Math.abs(newValue - this.currentValue) <= 1.0E-4)) {
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

        public void onClick(double mouseX, double mouseY) {
            this.sliding = true;
            this.commitQueued = false;
            this.interactionStartValue = this.currentValue;
            super.onClick(mouseX, mouseY);
        }

        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (!this.sliding) {
                this.sliding = true;
                this.interactionStartValue = this.currentValue;
            }

            super.onDrag(mouseX, mouseY, dragX, dragY);
        }

        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            if (this.sliding && (this.commitQueued || Math.abs(this.currentValue - this.interactionStartValue) > 1.0E-4)) {
                this.onCommit.run();
            }

            this.sliding = false;
            this.commitQueued = false;
        }

        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawString(WindTunnelMountScreen.this.font, this.label, this.getX(), this.getY() - 10, 4210752, false);
        }

        private void setExternalValue(double value) {
            this.currentValue = value;
            this.value = WindTunnelMountScreen.normalize(value, this.minValue, this.maxValue);
            this.updateMessage();
        }

        private boolean isSliding() {
            return this.sliding;
        }
    }

    private class DecimalEditBox extends EditBox {
        private final double minValue;
        private final double maxValue;
        private final DoubleConsumer onApply;

        private DecimalEditBox(
            Font font,
            int x,
            int y,
            int width,
            int height,
            double initialValue,
            double minValue,
            double maxValue,
            DoubleConsumer onApply
        ) {
            super(font, x, y, width, height, Component.empty());
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.onApply = onApply;
            this.setMaxLength(8);
            this.setFilter((value) -> value.isEmpty() || value.matches("-?\\d{0,3}(\\.\\d{0,2})?"));
            this.setValue(WindTunnelMountScreen.formatValue(initialValue));
        }

        public void setFocused(boolean focused) {
            boolean wasFocused = this.isFocused();
            super.setFocused(focused);
            if (wasFocused && !focused) {
                this.commitValue();
            }

        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!this.isFocused() || keyCode != 257 && keyCode != 335) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            } else {
                this.commitValue();
                this.setFocused(false);
                return true;
            }
        }

        private void commitValue() {
            if (!WindTunnelMountScreen.this.suppressUpdates && !this.getValue().isEmpty() && !"-".equals(this.getValue())) {
                double parsed = Mth.clamp(Double.parseDouble(this.getValue()), this.minValue, this.maxValue);
                String clamped = WindTunnelMountScreen.formatValue(parsed);
                if (!clamped.equals(this.getValue())) {
                    this.setValue(clamped);
                }

                this.onApply.accept(parsed);
            }
        }
    }
}
