package slimeknights.tconstruct.smeltery.client.inventory.module;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.mantle.client.screen.ElementScreen;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.client.GuiUtil;
import slimeknights.tconstruct.library.client.util.FluidTooltipHandler;
import slimeknights.tconstruct.library.network.TinkerNetwork;
import slimeknights.tconstruct.smeltery.network.SmelteryFluidClickedPacket;
import slimeknights.tconstruct.smeltery.tileentity.tank.SmelteryTank;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Helper class to draw the smeltery tank in UIs
 */
public class GuiSmelteryTank extends ElementScreen {
  // fluid tooltips
  public static final String TOOLTIP_CAPACITY = Util.makeTranslationKey("gui", "melting.capacity");
  public static final String TOOLTIP_AVAILABLE = Util.makeTranslationKey("gui", "melting.available");
  public static final String TOOLTIP_USED = Util.makeTranslationKey("gui", "melting.used");

  private final ContainerScreen<?> parent;
  private final SmelteryTank tank;
  public GuiSmelteryTank(ContainerScreen<?> parent, SmelteryTank tank, int x, int y, int w, int h) {
    super(x, y, w, h, 256, 256);
    this.parent = parent;
    this.tank = tank;
  }

  private int[] calcLiquidHeights() {
    assert tank != null;
    return calcLiquidHeights(tank.getFluids(), Math.max(tank.getContained(), tank.getCapacity()), h, 3);
  }

  /**
   * Checks if a position is within the tank
   * @param checkX  X position to check
   * @param checkY  Y position to check
   * @return  True if within the tank
   */
  private boolean withinTank(int checkX, int checkY) {
    return x <= checkX && checkX < (x + w) && y <= checkY && checkY < (y + h);
  }

  /**
   * Renders the smeltery tank
   * @param matrices  Matrix stack instance
   */
  public void renderFluids(MatrixStack matrices) {
    // draw liquids
    if (tank.getContained() > 0) {
      int[] heights = calcLiquidHeights();

      int bottom = y + w;
      for (int i = 0; i < heights.length; i++) {
        int fluidH = heights[i];
        FluidStack liquid = tank.getFluids().get(i);
        GuiUtil.renderTiledFluid(matrices, parent, liquid, x, bottom - fluidH, w, fluidH, 100);
        bottom -= fluidH;
      }
    }
  }

  /**
   * Renders a highlight on the hovered fluid
   * @param matrices  Matrix stack instance
   * @param mouseX    Mouse X
   * @param mouseY    Mouse Y
   */
  public void renderHighlight(MatrixStack matrices, int mouseX, int mouseY) {
    int checkX = mouseX - parent.guiLeft;
    int checkY = mouseY - parent.guiTop;
    if (withinTank(checkX, checkY)) {
      int[] heights = calcLiquidHeights();
      int hovered = getFluidHovered(heights, (y + h) - checkY - 1);

      // sum all heights below the hovered fluid
      int heightSum = 0;
      int loopMax = hovered == -1 ? heights.length : hovered + 1;
      for (int i = 0; i < loopMax; i++) {
        heightSum += heights[i];
      }
      // render the area
      if (hovered == -1) {
        GuiUtil.renderHighlight(matrices, x, y, w, h - heightSum);
      } else {
        GuiUtil.renderHighlight(matrices, x, (y + h) - heightSum, w, heights[hovered]);
      }
    }
  }

  /**
   * Gets the fluid under the mouse at the given Y position
   * @param heights  Fluids heights
   * @param y  Y position to check
   * @return  Fluid index under mouse, or -1 if no fluid
   */
  private int getFluidHovered(int[] heights, int y) {
    for (int i = 0; i < heights.length; i++) {
      if (y < heights[i]) {
        return i;
      }
      y -= heights[i];
    }

    return -1;
  }

  /**
   * Gets the tooltip for the tank based on the given mouse position
   * @param matrices  Matrix stack instance
   * @param mouseX    Mouse X
   * @param mouseY    Mouse Y
   */
  public void drawTooltip(MatrixStack matrices, int mouseX, int mouseY) {
    // Liquids
    int checkX = mouseX - parent.guiLeft;
    int checkY = mouseY - parent.guiTop;
    if (withinTank(checkX, checkY)) {
      int hovered = getFluidHovered(calcLiquidHeights(), (y + h) - checkY - 1);
      List<ITextComponent> tooltip;
      if (hovered == -1) {
        BiConsumer<Integer, List<ITextComponent>> formatter =
          Util.isShiftKeyDown() ? FluidTooltipHandler::appendBuckets : FluidTooltipHandler::appendIngots;

        tooltip = new ArrayList<>();
        tooltip.add(new TranslationTextComponent(TOOLTIP_CAPACITY));

        formatter.accept(tank.getCapacity(), tooltip);
        int remaining = tank.getRemainingSpace();
        if (remaining > 0) {
          tooltip.add(new TranslationTextComponent(TOOLTIP_AVAILABLE));
          formatter.accept(remaining, tooltip);
        }
        int used = tank.getContained();
        if (used > 0) {
          tooltip.add(new TranslationTextComponent(TOOLTIP_USED));
          formatter.accept(used, tooltip);
        }
        FluidTooltipHandler.appendShift(tooltip);
      }
      else {
        tooltip = FluidTooltipHandler.getFluidTooltip(tank.getFluidInTank(hovered));
      }
      parent.func_243308_b(matrices, tooltip, mouseX, mouseY);
    }
  }

  /**
   * Checks if the tank was clicked at the given location
   */
  public void handleClick(int mouseX, int mouseY) {
    if (withinTank(mouseX, mouseY)) {
      int index = getFluidHovered(calcLiquidHeights(), (y + h) - mouseY - 1);
      if (index != -1) {
        TinkerNetwork.getInstance().sendToServer(new SmelteryFluidClickedPacket(index));
      }
    }
  }


  /* Utils */

  /**
   * Calculate the rendering heights for all the liquids
   *
   * @param liquids  The liquids
   * @param capacity Max capacity of smeltery, to calculate how much height one liquid takes up
   * @param height   Maximum height, basically represents how much height full capacity is
   * @param min      Minimum amount of height for a fluid. A fluid can never have less than this value height returned
   * @return Array with heights corresponding to input-list liquids
   */
  public static int[] calcLiquidHeights(List<FluidStack> liquids, int capacity, int height, int min) {
    int[] fluidHeights = new int[liquids.size()];

    int totalFluidAmount = 0;
    if (liquids.size() > 0) {
      for(int i = 0; i < liquids.size(); i++) {
        FluidStack liquid = liquids.get(i);

        float h = (float) liquid.getAmount() / (float) capacity;
        totalFluidAmount += liquid.getAmount();
        fluidHeights[i] = Math.max(min, (int) Math.ceil(h * (float) height));
      }

      // if not completely full, leave a few pixels for the empty tank display
      if(totalFluidAmount < capacity) {
        height -= min;
      }

      // check if we have enough height to render everything, if not remove pixels from the tallest liquid
      int sum;
      do {
        sum = 0;
        int biggest = -1;
        int m = 0;
        for(int i = 0; i < fluidHeights.length; i++) {
          sum += fluidHeights[i];
          if(fluidHeights[i] > biggest) {
            biggest = fluidHeights[i];
            m = i;
          }
        }

        // we can't get a result without going negative
        if(fluidHeights[m] == 0) {
          break;
        }

        // remove a pixel from the biggest one
        if(sum > height) {
          fluidHeights[m]--;
        }
      } while(sum > height);
    }

    return fluidHeights;
  }
}