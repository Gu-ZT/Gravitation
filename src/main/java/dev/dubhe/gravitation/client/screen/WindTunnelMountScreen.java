package dev.dubhe.gravitation.client.screen;

import dev.dubhe.gravitation.block.entity.WindTunnelMountBlockEntity;
import dev.dubhe.gravitation.menu.WindTunnelMountMenu;
import dev.dubhe.gravitation.network.payload.UpdateWindTunnelMountPayload;
import dev.dubhe.gravitation.windtunnel.WindTunnelMountMeasurement;
import net.minecraft.client.Minecraft;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

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
    private static final int LABEL_COLOR = 0x404040;
    private static final int PANEL_COLOR = 0xFFF6EFE2;
    private static final int BORDER_COLOR = 0xFF87623A;
    private static final int ROW_FILL = 0x33A7855A;
    private static final int SECTION_LINE = 0xFFD8C7B0;
    private static final int BUTTON_ROW_Y = 26;
    private static final int FLOW_ROW_Y = 58;
    private static final int MEASUREMENT_TOP = 86;
    private static final int FIRST_SLIDER_Y = 128;
    private static final int SLIDER_SPACING = 26;

    private boolean suppressUpdates;
    private boolean locked;
    private Direction flowDirection = Direction.NORTH;
    private double angleOfAttack;
    private double sideslipAngle;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private WindTunnelMountMeasurement measurement = WindTunnelMountMeasurement.EMPTY;
    private boolean waitingForServerState;
    private int pendingSyncTicks;
    private boolean lastSentLocked;
    private Direction lastSentFlowDirection = Direction.NORTH;
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
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 12;
        this.titleLabelY = 10;
        // Read the latest server-backed state before constructing widgets.
        loadState();

        int left = leftPos;
        int top = topPos;

        lockButton = addRenderableWidget(Button.builder(
            Component.empty(),
            button -> {
                locked = !locked;
                updateLockButton();
                sendSettings(false);
            }
        ).bounds(left + 12, top + BUTTON_ROW_Y, 72, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("block.gravitation.wind_tunnel_mount.clear_binding"),
                button -> sendSettings(true)
            )
            .bounds(left + 92, top + BUTTON_ROW_Y, 92, 20)
            .build());

        flowDirectionButton = addRenderableWidget(CycleButton.<Direction>builder(direction -> Component.translatable(direction.getName()))
            .withValues(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN)
            .withInitialValue(flowDirection)
            .create(
                left + 12, top + FLOW_ROW_Y, 224, 20,
                Component.translatable("block.gravitation.wind_tunnel_mount.flow_direction"),
                (button, value) -> {
                    flowDirection = value;
                    if (!suppressUpdates) {
                        sendSettings(false);
                    }
                }
            ));

        angleSlider = addSlider(
            left, top, FIRST_SLIDER_Y, Component.translatable("block.gravitation.wind_tunnel_mount.angle_of_attack"),
            WindTunnelMountBlockEntity.MIN_ANGLE, WindTunnelMountBlockEntity.MAX_ANGLE,
            () -> angleOfAttack, value -> angleOfAttack = value, () -> syncField(angleField, angleOfAttack)
        );
        sideslipSlider = addSlider(
            left, top, FIRST_SLIDER_Y + SLIDER_SPACING, Component.translatable("block.gravitation.wind_tunnel_mount.sideslip_angle"),
            WindTunnelMountBlockEntity.MIN_ANGLE, WindTunnelMountBlockEntity.MAX_ANGLE,
            () -> sideslipAngle, value -> sideslipAngle = value, () -> syncField(sideslipField, sideslipAngle)
        );
        offsetXSlider = addSlider(
            left, top, FIRST_SLIDER_Y + SLIDER_SPACING * 2, Component.translatable("block.gravitation.wind_tunnel_mount.offset_x"),
            WindTunnelMountBlockEntity.MIN_OFFSET, WindTunnelMountBlockEntity.MAX_OFFSET,
            () -> offsetX, value -> offsetX = value, () -> syncField(offsetXField, offsetX)
        );
        offsetYSlider = addSlider(
            left, top, FIRST_SLIDER_Y + SLIDER_SPACING * 3, Component.translatable("block.gravitation.wind_tunnel_mount.offset_y"),
            WindTunnelMountBlockEntity.MIN_OFFSET, WindTunnelMountBlockEntity.MAX_OFFSET,
            () -> offsetY, value -> offsetY = value, () -> syncField(offsetYField, offsetY)
        );
        offsetZSlider = addSlider(
            left, top, FIRST_SLIDER_Y + SLIDER_SPACING * 4, Component.translatable("block.gravitation.wind_tunnel_mount.offset_z"),
            WindTunnelMountBlockEntity.MIN_OFFSET, WindTunnelMountBlockEntity.MAX_OFFSET,
            () -> offsetZ, value -> offsetZ = value, () -> syncField(offsetZField, offsetZ)
        );

        angleField = addField(
            left + 182, top + FIRST_SLIDER_Y, () -> angleOfAttack, value -> {
                angleOfAttack = value;
                angleSlider.setExternalValue(value);
                sendSettings(false);
            }, WindTunnelMountBlockEntity.MIN_ANGLE, WindTunnelMountBlockEntity.MAX_ANGLE
        );
        sideslipField = addField(
            left + 182, top + FIRST_SLIDER_Y + SLIDER_SPACING, () -> sideslipAngle, value -> {
                sideslipAngle = value;
                sideslipSlider.setExternalValue(value);
                sendSettings(false);
            }, WindTunnelMountBlockEntity.MIN_ANGLE, WindTunnelMountBlockEntity.MAX_ANGLE
        );
        offsetXField = addField(
            left + 182, top + FIRST_SLIDER_Y + SLIDER_SPACING * 2, () -> offsetX, value -> {
                offsetX = value;
                offsetXSlider.setExternalValue(value);
                sendSettings(false);
            }, WindTunnelMountBlockEntity.MIN_OFFSET, WindTunnelMountBlockEntity.MAX_OFFSET
        );
        offsetYField = addField(
            left + 182, top + FIRST_SLIDER_Y + SLIDER_SPACING * 3, () -> offsetY, value -> {
                offsetY = value;
                offsetYSlider.setExternalValue(value);
                sendSettings(false);
            }, WindTunnelMountBlockEntity.MIN_OFFSET, WindTunnelMountBlockEntity.MAX_OFFSET
        );
        offsetZField = addField(
            left + 182, top + FIRST_SLIDER_Y + SLIDER_SPACING * 4, () -> offsetZ, value -> {
                offsetZ = value;
                offsetZSlider.setExternalValue(value);
                sendSettings(false);
            }, WindTunnelMountBlockEntity.MIN_OFFSET, WindTunnelMountBlockEntity.MAX_OFFSET
        );

        updateLockButton();
        syncField(angleField, angleOfAttack);
        syncField(sideslipField, sideslipAngle);
        syncField(offsetXField, offsetX);
        syncField(offsetYField, offsetY);
        syncField(offsetZField, offsetZ);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (pendingSyncTicks > 0) {
            pendingSyncTicks--;
        }
        loadState();
    }

    @Override
    public void onClose() {
        commitFocusedFields();
        super.onClose();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER_COLOR);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, PANEL_COLOR);
        guiGraphics.fill(leftPos + 8, topPos + 48, leftPos + imageWidth - 8, topPos + 49, SECTION_LINE);
        guiGraphics.fill(leftPos + 8, topPos + MEASUREMENT_TOP - 4, leftPos + imageWidth - 8, topPos + MEASUREMENT_TOP + 26, ROW_FILL);
        guiGraphics.fill(leftPos + 8, topPos + MEASUREMENT_TOP + 34, leftPos + imageWidth - 8, topPos + MEASUREMENT_TOP + 35, SECTION_LINE);
        for (int i = 0; i < 5; i++) {
            int rowTop = topPos + FIRST_SLIDER_Y + i * SLIDER_SPACING - 4;
            guiGraphics.fill(leftPos + 8, rowTop, leftPos + imageWidth - 8, rowTop + 24, ROW_FILL);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        guiGraphics.drawString(
            font,
            Component.translatable("block.gravitation.wind_tunnel_mount.flow_direction"),
            12,
            50,
            LABEL_COLOR,
            false
        );
        guiGraphics.drawString(
            font,
            Component.translatable("block.gravitation.wind_tunnel_mount.measurement"),
            12,
            MEASUREMENT_TOP,
            LABEL_COLOR,
            false
        );
        guiGraphics.drawString(
            font,
            Component.literal(String.format(
                Locale.ROOT,
                "L %.2f  D %.2f  Y %.2f",
                measurement.lift(),
                measurement.drag(),
                measurement.sideForce()
            )),
            12,
            MEASUREMENT_TOP + 12,
            LABEL_COLOR,
            false
        );
        guiGraphics.drawString(
            font,
            Component.literal(String.format(
                Locale.ROOT,
                "P %.2f  R %.2f  N %.2f",
                measurement.pitchMoment(),
                measurement.rollMoment(),
                measurement.yawMoment()
            )),
            12,
            MEASUREMENT_TOP + 24,
            LABEL_COLOR,
            false
        );
    }

    private DecimalSlider addSlider(
        int left, int top, int y, Component label, double min, double max, Supplier<Double> getter,
        DoubleConsumer setter, Runnable syncField
    ) {
        DecimalSlider slider = new DecimalSlider(
            left + 12, top + y, SLIDER_WIDTH, label, min, max, getter.get(), value -> {
            setter.accept(value);
            syncField.run();
        }, () -> sendSettings(false)
        );
        addRenderableWidget(slider);
        return slider;
    }

    private DecimalEditBox addField(int x, int y, Supplier<Double> getter, DoubleConsumer onApply, double min, double max) {
        DecimalEditBox field = new DecimalEditBox(font, x, y, FIELD_WIDTH, 20, getter.get(), min, max, onApply);
        addRenderableWidget(field);
        return field;
    }

    private void loadState() {
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.level == null) {
            return;
        }

        if (!(minecraft.level.getBlockEntity(menu.getMountPos()) instanceof WindTunnelMountBlockEntity mount)) {
            return;
        }

        if ((angleSlider != null && angleSlider.isSliding())
            || (sideslipSlider != null && sideslipSlider.isSliding())
            || (offsetXSlider != null && offsetXSlider.isSliding())
            || (offsetYSlider != null && offsetYSlider.isSliding())
            || (offsetZSlider != null && offsetZSlider.isSliding())) {
            // Measurements should still update live even while the user is dragging controls.
            measurement = mount.getMeasurement();
            return;
        }

        boolean blockLocked = mount.isLocked();
        Direction blockFlowDirection = mount.getFlowDirection();
        double blockAngleOfAttack = mount.getAngleOfAttack();
        double blockSideslipAngle = mount.getSideslipAngle();
        double blockOffsetX = mount.getOffsetX();
        double blockOffsetY = mount.getOffsetY();
        double blockOffsetZ = mount.getOffsetZ();
        measurement = mount.getMeasurement();

        if (waitingForServerState) {
            // Preserve the local edit buffer briefly so the screen does not snap back while the
            // packet is still in flight or the next BE sync has not arrived yet.
            boolean serverCaughtUp = blockLocked == lastSentLocked
                                     && blockFlowDirection == lastSentFlowDirection
                                     && nearlyEquals(blockAngleOfAttack, lastSentAngleOfAttack)
                                     && nearlyEquals(blockSideslipAngle, lastSentSideslipAngle)
                                     && nearlyEquals(blockOffsetX, lastSentOffsetX)
                                     && nearlyEquals(blockOffsetY, lastSentOffsetY)
                                     && nearlyEquals(blockOffsetZ, lastSentOffsetZ);
            if (serverCaughtUp) {
                waitingForServerState = false;
                pendingSyncTicks = 0;
            } else if (pendingSyncTicks > 0) {
                return;
            } else {
                waitingForServerState = false;
            }
        }

        suppressUpdates = true;
        locked = blockLocked;
        flowDirection = blockFlowDirection;
        angleOfAttack = blockAngleOfAttack;
        sideslipAngle = blockSideslipAngle;
        offsetX = blockOffsetX;
        offsetY = blockOffsetY;
        offsetZ = blockOffsetZ;

        if (lockButton != null) {
            updateLockButton();
        }
        if (flowDirectionButton != null && flowDirectionButton.getValue() != flowDirection) {
            flowDirectionButton.setValue(flowDirection);
        }
        syncSliderAndField(angleSlider, angleField, angleOfAttack);
        syncSliderAndField(sideslipSlider, sideslipField, sideslipAngle);
        syncSliderAndField(offsetXSlider, offsetXField, offsetX);
        syncSliderAndField(offsetYSlider, offsetYField, offsetY);
        syncSliderAndField(offsetZSlider, offsetZField, offsetZ);
        suppressUpdates = false;
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
        if (lockButton != null) {
            lockButton.setMessage(Component.translatable(locked
                                                         ? "block.gravitation.wind_tunnel_mount.locked"
                                                         : "block.gravitation.wind_tunnel_mount.unlocked"));
        }
    }

    private void sendSettings(boolean clearBinding) {
        if (suppressUpdates) {
            return;
        }

        if (!clearBinding) {
            // Remember what we sent so we can distinguish "server has not answered yet" from
            // "server rejected or changed this state".
            waitingForServerState = true;
            pendingSyncTicks = CLIENT_EDIT_GRACE_TICKS;
            lastSentLocked = locked;
            lastSentFlowDirection = flowDirection;
            lastSentAngleOfAttack = angleOfAttack;
            lastSentSideslipAngle = sideslipAngle;
            lastSentOffsetX = offsetX;
            lastSentOffsetY = offsetY;
            lastSentOffsetZ = offsetZ;
        }

        PacketDistributor.sendToServer(new UpdateWindTunnelMountPayload(
            menu.getMountPos(),
            locked,
            flowDirection,
            angleOfAttack,
            sideslipAngle,
            offsetX,
            offsetY,
            offsetZ,
            clearBinding
        ));
    }

    private static String formatValue(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void commitFocusedFields() {
        if (angleField != null && angleField.isFocused()) {
            angleField.commitValue();
        }
        if (sideslipField != null && sideslipField.isFocused()) {
            sideslipField.commitValue();
        }
        if (offsetXField != null && offsetXField.isFocused()) {
            offsetXField.commitValue();
        }
        if (offsetYField != null && offsetYField.isFocused()) {
            offsetYField.commitValue();
        }
        if (offsetZField != null && offsetZField.isFocused()) {
            offsetZField.commitValue();
        }
    }

    private static boolean nearlyEquals(double left, double right) {
        return Math.abs(left - right) <= 1.0E-4D;
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
            int x, int y, int width, Component label, double minValue, double maxValue, double initialValue,
            DoubleConsumer onPreviewChange, Runnable onCommit
        ) {
            super(x, y, width, 20, Component.empty(), normalize(initialValue, minValue, maxValue));
            this.label = label;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.onPreviewChange = onPreviewChange;
            this.onCommit = onCommit;
            this.currentValue = initialValue;
            this.interactionStartValue = initialValue;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(formatValue(currentValue)));
        }

        @Override
        protected void applyValue() {
            double newValue = denormalize(this.value, minValue, maxValue);
            if (Math.abs(newValue - currentValue) <= 1.0E-4D) {
                return;
            }

            currentValue = newValue;
            updateMessage();
            onPreviewChange.accept(newValue);
            if (sliding) {
                // Preview continuously but emit one network commit when the drag gesture ends.
                commitQueued = true;
            } else {
                onCommit.run();
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            sliding = true;
            commitQueued = false;
            interactionStartValue = currentValue;
            super.onClick(mouseX, mouseY);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (!sliding) {
                sliding = true;
                interactionStartValue = currentValue;
            }
            super.onDrag(mouseX, mouseY, dragX, dragY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            if (sliding && (commitQueued || Math.abs(currentValue - interactionStartValue) > 1.0E-4D)) {
                onCommit.run();
            }
            sliding = false;
            commitQueued = false;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawString(font, label, getX(), getY() - 10, LABEL_COLOR, false);
        }

        private void setExternalValue(double value) {
            currentValue = value;
            this.value = normalize(value, minValue, maxValue);
            updateMessage();
        }

        private boolean isSliding() {
            return sliding;
        }
    }

    private class DecimalEditBox extends EditBox {
        private final double minValue;
        private final double maxValue;
        private final DoubleConsumer onApply;

        private DecimalEditBox(
            net.minecraft.client.gui.Font font, int x, int y, int width, int height, double initialValue,
            double minValue, double maxValue, DoubleConsumer onApply
        ) {
            super(font, x, y, width, height, Component.empty());
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.onApply = onApply;
            setMaxLength(8);
            setFilter(value -> value.isEmpty() || value.matches("-?\\d{0,3}(\\.\\d{0,2})?"));
            setValue(formatValue(initialValue));
        }

        @Override
        public void setFocused(boolean focused) {
            boolean wasFocused = isFocused();
            super.setFocused(focused);
            if (wasFocused && !focused) {
                commitValue();
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
                commitValue();
                setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private void commitValue() {
            if (suppressUpdates || getValue().isEmpty() || "-".equals(getValue())) {
                return;
            }
            // Clamp at commit time so typing stays permissive while the field is focused.
            double parsed = Mth.clamp(Double.parseDouble(getValue()), minValue, maxValue);
            String clamped = formatValue(parsed);
            if (!clamped.equals(getValue())) {
                setValue(clamped);
            }
            onApply.accept(parsed);
        }
    }

    private static double normalize(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    private static double denormalize(double value, double min, double max) {
        double scaled = min + value * (max - min);
        return Math.round(scaled * 100.0D) / 100.0D;
    }
}
