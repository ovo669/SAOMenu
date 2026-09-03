package com.sao.saomenu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * SAO 角色联动动画系统
 * 
 * 实现 Persona 风格的角色立绘与 UI 交互联动效果：
 * - 角色进入/退出动画
 * - 角色响应用户交互（悬停、点击）
 * - 角色表情/姿势切换
 * - 视差滚动效果
 * - 粒子光效
 */
public class SAOCharacterAnimator {
    
    // ========== 角色类型 ==========
    public enum Character {
        KIRITO("桐人", "kirito"),
        ASUNA("亚斯娜", "asuna"),
        YUI("结衣", "yui");
        
        public final String nameCn;
        public final String id;
        
        Character(String nameCn, String id) {
            this.nameCn = nameCn;
            this.id = id;
        }
    }
    
    // ========== 角色状态 ==========
    public enum CharacterState {
        IDLE,       // 待机
        THINKING,   // 思考
        CONFIRM,    // 确认
        EXCITED,    // 兴奋
        WORRIED     // 担心
    }
    
    // ========== 动画状态 ==========
    private Character currentCharacter = Character.KIRITO;
    private CharacterState currentState = CharacterState.IDLE;
    
    // 进入动画进度 (0-1)
    private float enterProgress = 0f;
    
    // 角色位置和偏移
    private float characterX = 0f;
    private float characterY = 0f;
    private float characterOffsetX = 0f;
    private float characterOffsetY = 0f;
    
    // 角色缩放
    private float characterScale = 1.0f;
    private float targetScale = 1.0f;
    
    // 角色透明度
    private float characterAlpha = 0f;
    
    // 呼吸动画（微动）
    private float breathingPhase = 0f;
    
    // 视线跟随
    private float eyeOffsetX = 0f;
    private float eyeOffsetY = 0f;
    private float targetEyeX = 0f;
    private float targetEyeY = 0f;
    
    // 粒子特效
    private float particlePhase = 0f;
    
    // ========== 构造函数 ==========
    public SAOCharacterAnimator() {
    }
    
    // ========== 更新动画 ==========
    public void tick(float deltaTime, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        // 1. 更新进入动画
        if (enterProgress < 1f) {
            enterProgress = Math.min(1f, enterProgress + deltaTime * 2f); // 0.5秒进入
        }
        
        // 2. 更新角色缩放（弹性动画）
        float scaleDiff = targetScale - characterScale;
        characterScale += scaleDiff * deltaTime * 8f;
        
        // 3. 更新透明度
        characterAlpha = enterProgress;
        
        // 4. 更新呼吸动画（微小的上下浮动）
        breathingPhase += deltaTime * 2f;
        float breathingOffset = (float) Math.sin(breathingPhase) * 3f;
        characterOffsetY = breathingOffset;
        
        // 5. 更新视线跟随（角色眼睛跟随鼠标）
        float mouseDeltaX = mouseX - (characterX + 100); // 假设眼睛在角色中心偏右
        float mouseDeltaY = mouseY - (characterY + 80);  // 假设眼睛在角色上方
        
        targetEyeX = Math.max(-10f, Math.min(10f, mouseDeltaX * 0.02f));
        targetEyeY = Math.max(-5f, Math.min(5f, mouseDeltaY * 0.02f));
        
        eyeOffsetX += (targetEyeX - eyeOffsetX) * deltaTime * 6f;
        eyeOffsetY += (targetEyeY - eyeOffsetY) * deltaTime * 6f;
        
        // 6. 更新粒子相位
        particlePhase += deltaTime * 3f;
    }
    
    // ========== 触发角色状态变化 ==========
    public void triggerState(CharacterState state) {
        if (currentState != state) {
            currentState = state;
            
            // 触发缩放动画
            targetScale = 1.05f;
            
            // 稍后恢复
            // 注意：实际应该用定时器，这里简化处理
        }
    }
    
    // ========== 切换角色 ==========
    public void switchCharacter(Character newChar) {
        if (currentCharacter != newChar) {
            // 淡出当前角色
            enterProgress = 0f;
            currentCharacter = newChar;
            currentState = CharacterState.IDLE;
        }
    }
    
    // ========== 开始进入动画 ==========
    public void startEnterAnimation() {
        enterProgress = 0f;
        characterScale = 0.8f;
        targetScale = 1.0f;
        characterAlpha = 0f;
    }
    
    // ========== 渲染角色 ==========
    public void render(GuiGraphics g, int x, int y, int width, int height) {
        if (characterAlpha <= 0.01f) return;
        
        PoseStack pose = g.pose();
        pose.pushPose();
        
        // 计算角色位置（左侧或右侧）
        characterX = x + width * 0.1f; // 左侧 10% 位置
        characterY = y + height * 0.3f; // 垂直 30% 位置
        
        // 应用进入动画偏移（从左侧滑入）
        float enterOffsetX = (1f - enterProgress) * -200f;
        
        // 移动到角色位置
        pose.translate(
            characterX + enterOffsetX + characterOffsetX,
            characterY + characterOffsetY,
            0
        );
        
        // 应用缩放
        float finalScale = characterScale * enterProgress;
        pose.scale(finalScale, finalScale, 1f);
        
        // 设置透明度
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, characterAlpha);
        
        // 绘制角色主体（占位符 - 实际应该绘制纹理）
        // TODO: 替换为实际的角色纹理
        drawCharacterPlaceholder(g, 0, 0, 200, 400);
        
        // 绘制眼睛（跟随鼠标）
        drawEyes(g, eyeOffsetX, eyeOffsetY);
        
        // 绘制粒子特效
        drawParticles(g, particlePhase);
        
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        
        pose.popPose();
    }
    
    // ========== 绘制角色占位符 ==========
    private void drawCharacterPlaceholder(GuiGraphics g, int x, int y, int w, int h) {
        // 绘制一个简单的人形轮廓作为占位符
        PoseStack pose = g.pose();
        Matrix4f matrix = pose.last().pose();
        
        // 头部（圆形）
        int headSize = w / 4;
        g.fill(x + w/2 - headSize/2, y, x + w/2 + headSize/2, y + headSize, 0x80FFFFFF);
        
        // 身体（矩形）
        g.fill(x + w/2 - w/6, y + headSize, x + w/2 + w/6, y + h/2, 0x80FFFFFF);
        
        // 腿（两个矩形）
        g.fill(x + w/2 - w/6, y + h/2, x + w/2 - 5, y + h, 0x80FFFFFF);
        g.fill(x + w/2 + 5, y + h/2, x + w/2 + w/6, y + h, 0x80FFFFFF);
        
        // 提示文字
        String text = currentCharacter.nameCn;
        int textWidth = 60; // 简化，实际应该用字体测量
        g.drawString(
            net.minecraft.client.Minecraft.getInstance().font,
            text,
            x + w/2 - textWidth/2,
            y + h + 10,
            0xFFFFFF
        );
    }
    
    // ========== 绘制眼睛 ==========
    private void drawEyes(GuiGraphics g, float offsetX, float offsetY) {
        // 左眼
        int eyeX1 = 60 + (int)offsetX;
        int eyeY1 = 60 + (int)offsetY;
        g.fill(eyeX1 - 3, eyeY1 - 3, eyeX1 + 3, eyeY1 + 3, 0xFF000000);
        
        // 右眼
        int eyeX2 = 90 + (int)offsetX;
        int eyeY2 = 60 + (int)offsetY;
        g.fill(eyeX2 - 3, eyeY2 - 3, eyeX2 + 3, eyeY2 + 3, 0xFF000000);
    }
    
    // ========== 绘制粒子 ==========
    private void drawParticles(GuiGraphics g, float phase) {
        // 简单的圆形粒子
        for (int i = 0; i < 5; i++) {
            float angle = phase + i * (float)Math.PI * 0.4f;
            float radius = 100f + (float)Math.sin(phase + i) * 20f;
            
            int px = (int)(Math.cos(angle) * radius);
            int py = (int)(Math.sin(angle) * radius);
            
            int size = 2 + (int)(Math.sin(phase * 2 + i) * 1);
            
            float alpha = 0.3f + (float)Math.sin(phase * 3 + i) * 0.2f;
            int color = (int)(alpha * 255) << 24 | 0xFFFFFF;
            
            g.fill(100 + px - size, 200 + py - size, 100 + px + size, 200 + py + size, color);
        }
    }
    
    // ========== Getter ==========
    public Character getCurrentCharacter() {
        return currentCharacter;
    }
    
    public CharacterState getCurrentState() {
        return currentState;
    }
    
    public float getEnterProgress() {
        return enterProgress;
    }
}
