/*
 * Copyright 2012 Licel LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.licel.jcardsim.utils;

import com.licel.jcardsim.smartcardio.JCSFactory;
import com.licel.jcardsim.smartcardio.JCardSimProvider;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import javax.smartcardio.*;

/**
 * Execute APDU script in C-APDU format.
 * @author LICEL LLC
 */
public class APDUScriptTool {
    // printing to output
    static boolean outputOn = true;

    public static void main(String args[]) throws FileNotFoundException, IOException, NoSuchAlgorithmException, CardException {
        if (args.length < 2) {
            System.out.println("Usage: java com.licel.jcardsim.utils.APDUScriptTool <jcardsim.cfg> <apdu script> [out file]");
            System.exit(-1);
        }
        Properties cfg = new Properties();
        // init Simulator
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(args[0]);
            cfg.load(fis);
        } catch (Throwable t) {
            System.err.println("Unable to load configuration " + args[0] + " due to: " + t.getMessage());
            System.exit(-1);
        } finally {
            if (fis != null) {
                fis.close();
            }
        }

        PrintStream out = args.length == 3 ? new PrintStream(args[2]) : System.out;
        fis = new FileInputStream(args[1]);
        try {
            executeCommands(cfg, fis, out);
        } catch (Throwable t) {
            System.err.println("Unable to execute " + args[1] + " due to: " + t.getMessage());
            System.exit(-1);
        } finally {
            if (fis != null) {
                fis.close();
            }
            if (args.length == 3 && out != null) {
                out.close();
            }
        }


    }

    public static void executeCommands(Properties cfg, InputStream commandsStream,
            PrintStream out) throws IOException, ParseException, NoSuchAlgorithmException, CardException {

        Enumeration keys = cfg.propertyNames();
        while(keys.hasMoreElements()) {
            String propertyName = (String) keys.nextElement();
            System.setProperty(propertyName, cfg.getProperty(propertyName));
        }

        ArrayList<CommandAPDU> commands = APDUScriptTool.parseAPDUStream(new InputStreamReader(commandsStream));
        if (Security.getProvider("jCardSim") == null) {
            JCardSimProvider provider = new JCardSimProvider();
            Security.addProvider(provider);
        }

        TerminalFactory.setProperties(new JCSFactory(), new JCardSimProvider());
        TerminalFactory tf = TerminalFactory.getInstance("jCardSim", null);
        CardTerminals ct = tf.terminals();
        List<CardTerminal> list = ct.list();
        CardTerminal jcsTerminal = null;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equals("jCardSim.Terminal")) {
                jcsTerminal = list.get(i);
                break;
            }
        }
        Card jcsCard = jcsTerminal.connect("T=0");
        CardChannel jcsChannel = jcsCard.getBasicChannel();
        if (commands.size() > 0) {
            for (int i = 0; i < commands.size(); i++) {
                CommandAPDU command = commands.get(i);
                ResponseAPDU response = jcsChannel.transmit(command);
                String dump = APDUScriptTool.commandToStr(command) + APDUScriptTool.responseToStr(response);
                if(out == null) {
                    System.out.println(dump);
                } else {
                    out.println(dump);
                }
            }
        }

    }

    private static ArrayList<CommandAPDU> parseAPDUStream(InputStreamReader in) throws IOException, ParseException {
        ArrayList<CommandAPDU> apduCommands = new ArrayList<CommandAPDU>();
        BufferedReader br = new BufferedReader(in);
        String line = br.readLine();
        StringBuilder command = new StringBuilder();
        while (line != null) {
            line = line.trim();
            if (line.startsWith("//") || line.isEmpty()) {
                line = br.readLine();
                continue;
            } else if (line.indexOf(";") < 0) {
                command.append(line);
            } else {
                command.append(line.substring(0, line.indexOf(";")));
                String cmd = command.toString();
                String[] words = cmd.split("\\s+");
                // skip some commands
                if(cmd.equalsIgnoreCase("powerup")
                    || cmd.equalsIgnoreCase("powerdown")
                    || cmd.equalsIgnoreCase("contacted")
                    || cmd.equalsIgnoreCase("contactless")) {
                    command = new StringBuilder();
                    command.append(line.substring(line.indexOf(";") + 1));
                    line = br.readLine();
                    continue;
                } else if(words.length>=6) {
                apduCommands.add(parseAPDUCommand(cmd));
                command = new StringBuilder();
                command.append(line.substring(line.indexOf(";") + 1));
                }
            }
            line = br.readLine();
        }
        return apduCommands;
    }

    private static int parseNumber(String str) {
        // hex number
        if (str.startsWith("0x")) {
            return Integer.parseInt(str.substring(2), 16);
        } else {
            return Integer.parseInt(str);
        }

    }

    private static String toHex(int i) {
        return i > 0xF ? Integer.toHexString(i) : "0" + Integer.toHexString(i);
    }
    
    private static CommandAPDU parseAPDUCommand(String command) throws ParseException {
        String[] bytes = command.split("\\s+");
        if (bytes.length < 6) {
            throw new ParseException("C-APDU format must be: <CLA> <INS> <P1> <P2> <LC> [<byte 0> <byte 1> ... <byte LC-1>] <LE>; "+command, 6);
        }
        int cla = parseNumber(bytes[0]);
        int ins = parseNumber(bytes[1]);
        int p0 = parseNumber(bytes[2]);
        int p1 = parseNumber(bytes[3]);
        int lc = parseNumber(bytes[4]);
        int le = parseNumber(bytes[bytes.length - 1]);
        // check lc
        if (lc + 6 > bytes.length) {
            throw new ParseException("Unexpected end of C-APDU: " + command, lc + 5);
        }
        byte[] data = new byte[lc];
        for (int i = 0; i < lc; i++) {
            data[i] = (byte) parseNumber(bytes[i + 5]);
        }
        return new CommandAPDU(cla, ins, p0, p1, data, le);
    }

    private static String commandToStr(CommandAPDU command) {
        StringBuilder sb = new StringBuilder();
        sb.append("CLA: ").append(toHex(command.getCLA())).append(", ");
        sb.append("INS: ").append(toHex(command.getINS())).append(", ");
        sb.append("P1: ").append(toHex(command.getP1())).append(", ");
        sb.append("P2: ").append(toHex(command.getP2())).append(", ");
        sb.append("Lc: ").append(toHex(command.getNc())).append(", ");
        byte[] data = command.getData();
        for (int i = 0; i < command.getNc(); i++) {
            sb.append(toHex(data[i] & 0xFF)).append(", ");
        }
        return sb.toString();
    }

    private static String responseToStr(ResponseAPDU response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Le: ").append(toHex(response.getNr())).append(", ");
        byte[] data = response.getData();
        for (int i = 0; i < data.length; i++) {
            sb.append(toHex(data[i] & 0xFF)).append(", ");
        }
        sb.append("SW1: ").append(toHex(response.getSW1())).append(", ");
        sb.append("SW2: ").append(toHex(response.getSW2()));
        return sb.toString();
    }
}
