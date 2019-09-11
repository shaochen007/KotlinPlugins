package com.cy.plugin.cost.visitor

import com.cy.plugin.cost.annotation.Cost
import org.objectweb.asm.*
import org.objectweb.asm.commons.AdviceAdapter

class CostClassVisitor(classVisitor: ClassVisitor) : ClassVisitor(Opcodes.ASM6, classVisitor) {


    override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(access: Int, name: String?, desc: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
        var mv = cv.visitMethod(access, name, desc, signature, exceptions)
        mv = object : AdviceAdapter(Opcodes.ASM6, mv, access, name, desc) {
            private var mInject: Boolean = false

            override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor {
                if (Type.getDescriptor(Cost::class.java) == desc) {
                    mInject = true
                }

                return super.visitAnnotation(desc, visible)
            }

            override fun onMethodEnter() {
                if (mInject) {
                    // 插入代码
                    println("在方法前插入代码")
                }
            }

            override fun onMethodExit(opcode: Int) {
                if (mInject && opcode == Opcodes.RETURN) {
                    println("在方法后插入代码")
                }
            }
        }
        return mv
    }
}