package arconyx.enchanterslibrary.mixin.client;

import arconyx.enchanterslibrary.AugmentedEnchantingScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends HandledScreen<EnchantmentScreenHandler> {

    @Shadow
    public float nextPageTurningSpeed;

    public EnchantmentScreenMixin(EnchantmentScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "render", at = @At(value = "TAIL"))
    protected void drawBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        if ((this.handler instanceof AugmentedEnchantingScreen bookshelfHandler) && (this.nextPageTurningSpeed > 0F)) {
            context.getMatrices().push();
            context.getMatrices().translate(i + 33.0F, j + 31.0F, 120.0F);
            String bookCount = String.valueOf(bookshelfHandler.enchanters_library$getBookshelves());
            context.drawCenteredTextWithShadow(this.textRenderer, bookCount, 0, -this.textRenderer.fontHeight / 2 - 1, 8453920);
            context.getMatrices().pop();
        }
    }
}
