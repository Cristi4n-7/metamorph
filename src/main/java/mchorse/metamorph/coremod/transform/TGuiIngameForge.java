package mchorse.metamorph.coremod.transform;

import java.util.ListIterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import mchorse.metamorph.coremod.ObfuscatedName;
import net.minecraft.launchwrapper.IClassTransformer;

public class TGuiIngameForge implements IClassTransformer
{
    public static final String GUI_INGAME_FORGE = "net.minecraftforge.client.GuiIngameForge";
    public static final ObfuscatedName RENDER_GAME_OVERLAY = new ObfuscatedName("renderGameOverlay", "func_175180_a");
    
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass)
    {
        if (transformedName.equals(GUI_INGAME_FORGE))
        {
            return transformClass(basicClass);
        }
        return basicClass;
    }
    
    private byte[] transformClass(byte[] basicClass)
    {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode visitor = new ClassNode();
        reader.accept(visitor, 0);
        
        for (MethodNode method : visitor.methods)
        {
            if (method.name.equals(RENDER_GAME_OVERLAY.getName()))
            {
                InsnList instructions = method.instructions;
                
                AbstractInsnNode entryPoint = null;
                AbstractInsnNode possibleEntryPoint = null;
                ListIterator<AbstractInsnNode> iterator = instructions.iterator();
                
                int entryPointProgress = 0;
                
                /*
                 * We want to patch before this line:
                 * if (renderAir)    renderAir(width, height);
                 */
                while (iterator.hasNext() && entryPoint == null)
                {
                    AbstractInsnNode insn = iterator.next();
                    
                    switch (entryPointProgress)
                    {
                    case 0:
                        if (insn.getOpcode() == Opcodes.GETSTATIC &&
                        ((FieldInsnNode)insn).name.equals("renderAir"))
                        {
                            possibleEntryPoint = insn;
                            entryPointProgress++;
                        }
                    break;
                    case 1:
                        if (insn.getOpcode() == Opcodes.IFEQ)
                        {
                            entryPoint = possibleEntryPoint;
                        }
                    break;
                    }
                }
                
                if (entryPoint != null)
                {
                    InsnList patch = new InsnList();
                    
                    // Get GuiIngameForge.eventParent (RenderGameOverlayEvent)
                    patch.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    patch.add(new FieldInsnNode(Opcodes.GETFIELD,
                            GUI_INGAME_FORGE.replaceAll("\\.", "/"),
                            "eventParent",
                            "Lnet/minecraftforge/client/event/RenderGameOverlayEvent;"));
                    // Get field partialTicks (float)
                    patch.add(new VarInsnNode(Opcodes.FLOAD, 1));
                    // Get GuiIngameForge.res (ScaledResolution)
                    patch.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    patch.add(new FieldInsnNode(Opcodes.GETFIELD,
                            GUI_INGAME_FORGE.replaceAll("\\.", "/"),
                            "res",
                            "Lnet/minecraft/client/gui/ScaledResolution;"));
                    // Event hook for AirPossiblyRenderedEvent
                    patch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "mchorse/metamorph/client/AirPossiblyRenderingEvent",
                            "hook",
                            "(Lnet/minecraftforge/client/event/RenderGameOverlayEvent;" +
                            "FLnet/minecraft/client/gui/ScaledResolution;)V",
                            false));
                    
                    instructions.insertBefore(entryPoint, patch);
                }
            }
        }
        
        ClassWriter writer = new ClassWriter(0);
        visitor.accept(writer);
        byte[] newClass = writer.toByteArray();
        
        return newClass;
    }

}
