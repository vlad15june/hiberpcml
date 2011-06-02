/*
 * The MIT License
 *
 * Copyright 2011 John Arevalo <johnarevalo@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.googlecode.hiberpcml;

import com.ibm.as400.access.AS400Message;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.data.ProgramCallDocument;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author John Arevalo <johnarevalo@gmail.com>
 */
public class Manager {

    private AS400 as400;
    private ProgramCallDocument pcmlDoc;
    private String libraries;
    private Properties configuration;

    public Manager() {
    }

    public void invoke(Object pcml) throws PcmlException {
        Command command = pcml.getClass().getAnnotation(Command.class);
        try {
            pcmlDoc = new ProgramCallDocument(as400, command.documentName());
            Field[] fields = pcml.getClass().getDeclaredFields();
            for (Field field : fields) {
                setValue(field, pcml, command.programName());
            }

            pcmlDoc.callProgram(command.programName());

            AS400Message[] messageList = pcmlDoc.getProgramCall().getMessageList();
            if (messageList != null && messageList.length > 0) {
                StringBuilder buffer = new StringBuilder();
                for (AS400Message message : messageList) {
                    buffer.append(message.getText()).append(System.getProperty("line.separator"));
                }
                throw new PcmlException(buffer.toString());
            }
            for (Field field : fields) {
                getValue(field, pcml, command.programName());
            }
        } catch (com.ibm.as400.data.PcmlException exc) {
            // on every error reset connection, in order to avoid blocking 
            resetConnection();
            throw new PcmlException(exc);
        } catch (Exception ex) {
            throw new PcmlException(ex);
        }
    }

    private void resetConnection() {
        if (as400 != null) {
            try {
                as400.disconnectAllServices();
            } catch (Exception ex) {
                Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (configuration == null) {
            throw new IllegalStateException("This instance has not been configured yet");
        }
        libraries = configuration.getProperty("as400.pcml.libraries");
        as400 = new AS400(
                configuration.getProperty("as400.pcml.host"),
                configuration.getProperty("as400.pcml.user"),
                configuration.getProperty("as400.pcml.password"));
        CommandCall commandCall = new CommandCall(as400);
        try {
            commandCall.run("CHGLIBL LIBL(" + libraries + ")");
        } catch (Exception ex) {
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
        }
        //suggested values to logging
        int logLevel = 4;
        int logSeverity = 30;
        try {
            logLevel = Integer.parseInt(configuration.getProperty("as400.pcml.loglevel"));
        } catch (Exception ex) {
        }
        try {
            logSeverity = Integer.parseInt(configuration.getProperty("as400.pcml.logseverity"));
        } catch (Exception ex) {
        }
        try {
            commandCall.run("CHGJOB LOG(" + logLevel + " " + logSeverity + " *SECLVL) LOGCLPGM(*YES) INQMSGRPY(*DFT) LOGOUTPUT(*JOBEND)");
        } catch (Exception ex) {
            Logger.getLogger(Manager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private void setValue(Field field, Object valueField, String path) throws Exception {
        if (field.isAnnotationPresent(Element.class)) {
            Element pcmlElement = field.getAnnotation(Element.class);
            if (!pcmlElement.usage().equals(UsageType.INPUT)
                    && !pcmlElement.usage().equals(UsageType.INPUTOUTPUT)) {
                return;
            }
            Object value = getField(field, valueField);
            path = path + "." + pcmlElement.pcmlName();

            if (field.getType().isAnnotationPresent(Struct.class)) {
                for (Field structField : field.getType().getDeclaredFields()) {
                    setValue(structField, value, path);
                }
            } else {
                if (value instanceof String) {
                    value = Util.completeWith((String) value, pcmlElement.completeWith(), pcmlElement.size());
                }
                pcmlDoc.setValue(path, value);
            }

        } else if (field.isAnnotationPresent(Array.class)) {
            Object value;
            Array pcmlArray = field.getAnnotation(Array.class);
            if (!pcmlArray.usage().equals(UsageType.INPUT)
                    && !pcmlArray.usage().equals(UsageType.INPUTOUTPUT)) {
                return;
            }
            path = path + "." + pcmlArray.pcmlName();
            List arrayValue = (List) getField(field, valueField);

            if (pcmlArray.type().isAnnotationPresent(Struct.class)) {
                for (Field structField : pcmlArray.type().getDeclaredFields()) {
                    if (structField.isAnnotationPresent(Element.class)) {
                        Element pcmlElement = structField.getAnnotation(Element.class);
                        int indices[] = new int[1];
                        for (int i = 0; i < pcmlArray.size(); i++) {
                            indices[0] = i;
                            try {
                                value = getField(structField, arrayValue.get(i));
                            } catch (Exception ex) {
                                value = "0";
                            }
                            try {
                                if (value instanceof String) {
                                    value = Util.completeWith((String) value, pcmlElement.completeWith(), pcmlElement.size());
                                }
                                pcmlDoc.setValue(path + "." + pcmlElement.pcmlName(), indices, value);
                            } catch (Exception exc) {
                                throw new Exception("Error setting \"" + path + "." + pcmlElement.pcmlName()
                                        + "\" with value '" + value + "' (" + exc.getMessage() + ")");
                            }
                        }
                    }
                }
            } else {
                int indices[] = new int[1];
                for (int i = 0; i < pcmlArray.size(); i++) {
                    indices[0] = i;
                    try {
                        value = arrayValue.get(i);
                    } catch (Exception ex) {
                        value = " ";
                    }
                    pcmlDoc.setValue(path, indices, value);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void getValue(Field field, Object valueField, String path) throws Exception {
        if (field.isAnnotationPresent(Element.class)) {
            Element pcmlElement = field.getAnnotation(Element.class);
            if (field.getType().isAnnotationPresent(Struct.class)) {
                Object struct = getField(field, valueField);

                for (Field structField : field.getType().getDeclaredFields()) {
                    getValue(structField, struct, path + "." + pcmlElement.pcmlName());
                }
            } else {
                Object value = pcmlDoc.getValue(path + "." + pcmlElement.pcmlName());
                setField(field, valueField, value);
            }
        } else if (field.isAnnotationPresent(Array.class)) {
            Array pcmlArray = field.getAnnotation(Array.class);
            List<Object> array = (List<Object>) getField(field, valueField);
            array.clear();
            path = path + "." + pcmlArray.pcmlName();
            int indices[] = new int[1];
            if (pcmlArray.type().isAnnotationPresent(Struct.class)) {
                for (int i = 0; i < pcmlArray.size(); i++) {
                    indices[0] = i;
                    Object elementArray = pcmlArray.type().getDeclaredConstructor().newInstance();
                    for (Field structField : pcmlArray.type().getDeclaredFields()) {
                        if (structField.isAnnotationPresent(Element.class)) {
                            Element pcmlElement = structField.getAnnotation(Element.class);
                            Object value = pcmlDoc.getValue(path + "." + pcmlElement.pcmlName(), indices);
                            setField(structField, elementArray, value);
                        }
                    }

                    array.add(elementArray);
                }
            } else {

                for (int i = 0; i < pcmlArray.size(); i++) {
                    indices[0] = i;
                    Object value = pcmlDoc.getValue(path, indices);
                    array.add(value);
                }

                setField(field, valueField, array);
            }
        }
    }

    public static Object getField(Field field, Object object) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
        String methodName = "get" + Util.toCamelCase(field.getName());
        Method method = field.getDeclaringClass().getMethod(methodName);
        return method.invoke(object);
    }

    public static void setField(Field field, Object object, Object value) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
        String methodName = "set" + Util.toCamelCase(field.getName());
        Method method = field.getDeclaringClass().getMethod(methodName, value.getClass());
        method.invoke(object, value);
    }

    public Properties getConfiguration() {
        return configuration;
    }

    /**
     * 
     * @param configuration 
     */
    public void setConfiguration(Properties configuration) {
        this.configuration = configuration;
        resetConnection();
    }
}
