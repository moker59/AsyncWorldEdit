/*
 * AsyncWorldEdit a performance improvement plugin for Minecraft WorldEdit plugin.
 * AsyncWorldEdit Injector a hack plugin that allows AsyncWorldEdit to integrate with
 * the WorldEdit plugin.
 *
 * Copyright (c) 2014, SBPrime <https://github.com/SBPrime/>
 * Copyright (c) AsyncWorldEdit contributors
 * Copyright (c) AsyncWorldEdit injector contributors
 *
 * All rights reserved.
 *
 * Redistribution in source, use in source and binary forms, with or without
 * modification, are permitted free of charge provided that the following
 * conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 * 2.  Redistributions of source code, with or without modification, in any form
 *     other then free of charge is not allowed,
 * 3.  Redistributions of source code, with tools and/or scripts used to build the 
 *     software is not allowed,
 * 4.  Redistributions of source code, with information on how to compile the software
 *     is not allowed,
 * 5.  Providing information of any sort (excluding information from the software page)
 *     on how to compile the software is not allowed,
 * 6.  You are allowed to build the software for your personal use,
 * 7.  You are allowed to build the software using a non public build server,
 * 8.  Redistributions in binary form in not allowed.
 * 9.  The original author is allowed to redistrubute the software in bnary form.
 * 10. Any derived work based on or containing parts of this software must reproduce
 *     the above copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided with the
 *     derived work.
 * 11. The original author of the software is allowed to change the license
 *     terms or the entire license of the software as he sees fit.
 * 12. The original author of the software is allowed to sublicense the software
 *     or its parts using any license terms he sees fit.
 * 13. By contributing to this project you agree that your contribution falls under this
 *     license.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.primesoft.asyncworldedit.injector.core;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.primesoft.asyncworldedit.injector.IClassInjector;
import org.primesoft.asyncworldedit.injector.classfactory.IClassFactory;
import org.primesoft.asyncworldedit.injector.classfactory.base.BaseClassFactory;
import org.primesoft.asyncworldedit.injector.core.visitors.BlockArrayClipboardClassVisitor;
import org.primesoft.asyncworldedit.injector.core.visitors.EditSessionClassVisitor;
import org.primesoft.asyncworldedit.injector.core.visitors.FlattenedClipboardTransformClassVisitor;
import org.primesoft.asyncworldedit.injector.core.visitors.ForwardExtentCopyClassVisitor;
import org.primesoft.asyncworldedit.injector.core.visitors.ICreateClass;
import org.primesoft.asyncworldedit.injector.core.visitors.InjectorClassVisitor;
import org.primesoft.asyncworldedit.injector.core.visitors.OperationsClassVisitor;
import org.primesoft.asyncworldedit.injector.core.visitors.SnapshotUtilCommandsVisitor;

/**
 *
 * @author SBPrime
 */
public class InjectorCore {

    /**
     * Injector core instance
     */
    private static InjectorCore s_instance = null;

    /**
     * Instance MTA mutex
     */
    private final static Object s_mutex = new Object();

    /**
     * Get the static instance
     *
     * @return
     */
    public static InjectorCore getInstance() {
        if (s_instance == null) {
            synchronized (s_mutex) {
                if (s_instance == null) {
                    s_instance = new InjectorCore();
                }
            }
        }

        return s_instance;
    }

    /**
     * The platform specific API
     */
    private IInjectorPlatform m_platform;

    /**
     * The WorldEdit class factory
     */
    private IClassFactory m_classFactory = new BaseClassFactory();

    private IClassInjector m_classInjector;

    /**
     * The MTA access mutex
     */
    private final Object m_mutex = new Object();

    /**
     * Log a console message
     *
     * @param message
     */
    private void log(String message) {
        IInjectorPlatform platform = m_platform;
        if (platform == null) {
            return;
        }

        m_platform.log(message);
    }

    /**
     * Initialize the injector core
     *
     * @param platform
     */
    public void initialize(IInjectorPlatform platform, IClassInjector classInjector) {
        synchronized (m_mutex) {
            if (m_platform != null) {
                log("Injector platform is already set to "
                        + m_platform.getPlatformName() + "."
                        + "Ignoring new platform " + platform.getPlatformName());
                return;
            }

            m_platform = platform;
            m_classInjector = classInjector;

            log("Injector platform set to: " + platform.getPlatformName());
        }

        log("Injecting WorldEdit classes...");
        try {
            modiffyClasses("com.sk89q.worldedit.EditSession", c -> new EditSessionClassVisitor(c));
            modiffyClasses("com.sk89q.worldedit.function.operation.Operations", (c, cc) -> new OperationsClassVisitor(c, cc));
            modiffyClasses("com.sk89q.worldedit.function.operation.ForwardExtentCopy", c -> new ForwardExtentCopyClassVisitor(c));
            modiffyClasses("com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard", (c, cc) -> new BlockArrayClipboardClassVisitor(c, cc));
            modiffyClasses("com.sk89q.worldedit.command.FlattenedClipboardTransform", (c, cc) -> new FlattenedClipboardTransformClassVisitor(c, cc));
            modiffyClasses("com.sk89q.worldedit.command.SnapshotUtilCommands", (c, cc) -> new SnapshotUtilCommandsVisitor(c, cc));
        } catch (Throwable ex) {
            log("****************************");
            log("* CLASS INJECTION FAILED!! *");
            log("****************************");
            log("* AsyncWorldEdit won't work properly.");
            log("* Exception: " + ex.getClass().getName());
            log("* Error message: " + ex.getLocalizedMessage());
            log("* Stack:");
            for (StackTraceElement element : ex.getStackTrace()) {
                log("* " + element.toString());
            }
            log("****************************");
        }
    }

    /**
     * Set new class factory
     *
     * @param factory
     */
    public void setClassFactory(IClassFactory factory) {
        synchronized (m_mutex) {
            if (factory == null) {
                factory = new BaseClassFactory();
                log("New class factory set to default factory.");
            } else {
                log("New class factory set to: " + factory.getClass().getName());
            }

            m_classFactory = factory;
        }
    }

    /**
     * Get the class factory
     *
     * @return
     */
    public IClassFactory getClassFactory() {
        return m_classFactory;
    }

    /**
     * getInjectorVersion The injector version
     *
     * @return
     */
    public double getVersion() {
        return 1.0600;
    }

    private void modiffyClasses(String className, Function<ClassWriter, InjectorClassVisitor> classVisitor) throws IOException {
        log("Modiffy class " + className);
        
        ClassReader classReader = m_classInjector.getClassReader(className);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        InjectorClassVisitor icv = classVisitor.apply(classWriter);

        classReader.accept(icv, 0);
        icv.validate();

        byte[] data = classWriter.toByteArray();

        try (DataOutputStream dout = new DataOutputStream(new FileOutputStream(new File("./classes/" + className + ".class")))) {
            dout.write(data);
        }

        m_classInjector.injectClass(className, data, 0, data.length);
    }

    private void modiffyClasses(String className,
            BiFunction<ClassWriter, ICreateClass, InjectorClassVisitor> classVisitor) throws IOException {
        log("Modiffy class " + className);
                
        ClassReader classReader = m_classInjector.getClassReader(className);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        InjectorClassVisitor icv = classVisitor.apply(classWriter, this::createClasses);
        classReader.accept(icv, 0);
       
        icv.validate();

        byte[] data = classWriter.toByteArray();

        try (DataOutputStream dout = new DataOutputStream(new FileOutputStream(new File("./classes/" + className + ".class")))) {
            dout.write(data);
        }

        m_classInjector.injectClass(className, data, 0, data.length);
    }

    private void createClasses(String className, ClassWriter classWriter) throws IOException {
        byte[] data = classWriter.toByteArray();

        try (DataOutputStream dout = new DataOutputStream(new FileOutputStream(new File("./classes/" + className + ".class")))) {
            dout.write(data);
        }

        m_classInjector.injectClass(className, data, 0, data.length);
    }

}
