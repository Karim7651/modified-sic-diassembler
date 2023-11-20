import java.io.*;
import java.util.*;

public class HTE {
    public String location;
    public String startingAddress;
    public String endingAddress;

    //relative path starts at packages not current file
    public String File = "src\\input.txt";
    public String outputFile = "src\\output.txt";

    public String symbolTableFile="src\\symbolTable.txt";
    public ArrayList<ArrayList<String>> codeFirstPart = new ArrayList<>();


    public ArrayList<ArrayList<String>> codeSecondPart = new ArrayList<>(); //for RESW RESB WORD BYTE
    public HashMap<String, String> codeSecondPartMap = new HashMap<>();
    public ArrayList<String> symbolTable = new ArrayList<>();
    public HashSet<String> jumpAddresses = new HashSet<>(); //
    public HashMap<String, String> declaredVariablesMap = new HashMap<>(); // key->address , value -> variableName

    public ArrayList<String> inputFile = new ArrayList<>(); //hte record array
    public String reference = "REF";
    int referenceNumber = 0;
    public HashMap<String, String> instructionOPCodeMap = new HashMap<>();

    public HashMap<String, String> instructionFormatOneOPCodeMap = new HashMap<>();
    public HashMap<String, String> instructionVariableTypeMap = new HashMap<>();
    public HTE() {
        readFromFile();
        initializeMaps();
        processHRecord(inputFile.get(0));
        processTRecords();
        removeExcess();
        sortSecondPart();
        calculateReferencesOfVariables();
        generateSymbolTable();
        processERecord();
        writeToFile();
        writeSymbolTable();
    }

    public void generateSymbolTable() {
        for (int i = 0; i < codeSecondPart.size(); i++) {
            ArrayList<String> list = codeSecondPart.get(i);
            String symbol = list.get(1);
            String location = list.get(0);
            symbolTable.add(symbol);
            symbolTable.add(location);
        }
    }

    public void calculateReferencesOfVariables() {
        //RESW RESB
        for (int i = 0; i < codeSecondPart.size(); i++) {
            ArrayList<String> current = codeSecondPart.get(i);
            for (int j = 0; j < current.size(); j++) {
                if (current.get(2).equals("resw") || current.get(2).equals("RESW")) {
                    String value = subtractTwoHexadecimalAndDivideBy3(codeSecondPart.get(i + 1).get(0), current.get(0));
                    current.set(3, String.valueOf(Integer.parseInt(value, 16)));
                } else if (current.get(2).equals("resb") || current.get(2).equals("RESB")) {
                    String value = subtractTwoHexadecimal(codeSecondPart.get(i + 1).get(0), current.get(0));
                    current.set(3, String.valueOf(Integer.parseInt(value, 16)));
                }
            }
        }
        //word byte, set object code too
        String current = inputFile.get(3);
        ArrayList<String> list = new ArrayList<>();
        int counter = 0;
        for (int i = 9; i < current.length(); i += 6) {
            list.add(current.substring(i, i + 6));
        }
        for (int i = 0; i < codeSecondPart.size(); i++) {
            ArrayList<String> current1 = codeSecondPart.get(i);
            if (current1.get(2).equals("word") || current1.get(2).equals("WORD")) {
                //object code
                String value = list.get(counter);
                current1.set(4, value);
                //object code from hex to decimal string
                value = String.valueOf(Integer.parseInt(value, 16));

                current1.set(3, value);
                counter++;
            }

        }
    }

    public void sortSecondPart() {
        Collections.sort(codeSecondPart, Comparator.comparingInt(list -> hexadecimalToDecimal(list.get(0))));
    }

    public void removeExcess() {
        //remove excess instructions
        int size1 = codeFirstPart.size();
        int size2 = codeSecondPart.size();
        for (int i = 1; i < 5; i++) {
            codeFirstPart.remove(size1 - i);
            codeSecondPart.remove(size2 - i);

        }
    }

    public void readFromFile() {
        //read from file put every line in an index in arraylist
        try (BufferedReader reader = new BufferedReader(new FileReader(File))) {
            String line;
            while ((line = reader.readLine()) != null) {
                inputFile.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(inputFile);
    }

    public void prepareHeader() {
        ArrayList<String> header = new ArrayList<>();
        header.add("Location");
        header.add("Label");
        header.add("Instr.");
        header.add("Ref");
        header.add("OBCode");
        codeFirstPart.add(header);
    }

    public void processHRecord(String HRecord) {
        prepareHeader();
        startingAddress = HRecord.substring(HRecord.length() - 12, HRecord.length() - 6);
        startingAddress = cutFirstTwoCharacters(startingAddress);
        String programName = HRecord.substring(1, HRecord.length() - 12);
        endingAddress = cutFirstTwoCharacters(HRecord.substring(HRecord.length() - 6));
        endingAddress = addTwoHexadecimal(startingAddress, endingAddress);
        location = startingAddress;
        ArrayList<String> firstLine = new ArrayList<>();
        prepList(firstLine);
        firstLine.set(1, programName);
        firstLine.set(2, "START");
        firstLine.set(3, startingAddress);
        codeFirstPart.add(firstLine);
    }

    public void processTRecords() {
        boolean isFormatOne = false;
        for (int i = 1; i < inputFile.size() - 1; i++) {
            String current = inputFile.get(i);
            for (int j = 9; j < current.length(); ) {

                ArrayList<String> list = new ArrayList<>();
                prepList(list);
                String instructionName;
                String objectCode;
                String ref;
                //check is it format1 or format 3/4
                String OPCode = current.substring(j, j + 2);
                if (instructionFormatOneOPCodeMap.containsKey(OPCode)) {
                    isFormatOne = true;
                } else {
                    isFormatOne = false;
                }
                //format one here
                if (isFormatOne) {
                    list.set(0, location);
                    jumpAddresses.add(location);
                    location = addTwoHexadecimal(location, "1");
                    instructionName = instructionFormatOneOPCodeMap.get(OPCode);
                    list.set(2, instructionName);
                    //no reference here
                    list.set(4, OPCode);
                    codeFirstPart.add(list);

                } else {
                    //format 3/4 here
                    objectCode = current.substring(j, j + 6);
                    String address = cutFirstTwoCharacters(objectCode);
                    ref = getAndIncrementReference();
                    instructionName = instructionOPCodeMap.get(OPCode);
                    //handle jump instruction labels
                    //if it is a jump instruction then go find this row and add same label to it
                    if (OPCode.equals("3C") || OPCode.equals("30") || OPCode.equals("34") || OPCode.equals("38")) {
                        int row = findRowUsingLocation(address);
                        codeFirstPart.get(row).set(1, ref);
                        //no variable deceleration here
                        list.set(0, location);
                        list.set(2, instructionName);
                        list.set(3, ref);
                        list.set(4, objectCode);
                        codeFirstPart.add(list);
                        symbolTable.add(ref);
                        symbolTable.add(address);
                    }
                    if (isImmediateInstruction(objectCode)) {
                        instructionName = getImmediateInstruction(objectCode);
                        list.set(0, location);
                        //no label here
                        list.set(2, instructionName);
                        //no ref here
                        list.set(4, objectCode);
                        codeFirstPart.add(list);
                        //no variable deceleration needed for immediate
                        jumpAddresses.add(address);
                    }
                    if (isCommaXInstruction(objectCode)) {
                        String variableAddress = getCommaXAddress(objectCode);
                        list.set(0, location);
                        //no label here
                        list.set(2, instructionName);
                        list.set(3, ref + ",X");
                        list.set(4, objectCode);
                        codeFirstPart.add(list);
                        declaredVariablesMap.put(address, ref);
                        jumpAddresses.add(address);
                        // declare for resw / resb / word / byte in codeSecondPart ArrayList
                        if (!codeSecondPartMap.containsKey(address)) {
                            ArrayList<String> list2 = new ArrayList<>();
                            String type = instructionVariableTypeMap.get(OPCode);
                            prepList(list2);
                            list2.set(0, variableAddress);
                            list2.set(1, ref);
                            list2.set(2, type);
                            codeSecondPart.add(list2);
                            codeSecondPartMap.put(address, ref);

                        }
                    }
                    //normal instructions
                    if (!OPCode.equals("3C") && !OPCode.equals("30") && !OPCode.equals("34") && !OPCode.equals("38") && !isImmediateInstruction(objectCode) && !isCommaXInstruction(objectCode)) {
                        if (declaredVariablesMap.containsKey(address)) {
                            ref = declaredVariablesMap.get(address);
                            referenceNumber--;
                        }
                        list.set(0, location);
                        //no label here
                        list.set(2, instructionName);
                        list.set(3, ref);
                        list.set(4, objectCode);
                        declaredVariablesMap.put(address, ref);
                        codeFirstPart.add(list);
                        // declare for resw / resb / word / byte in codeSecondPart ArrayList
                        if (!codeSecondPartMap.containsKey(address)) {
                            ArrayList<String> list2 = new ArrayList<>();
                            String type = instructionVariableTypeMap.get(OPCode);
                            prepList(list2);
                            list2.set(0, address);
                            list2.set(1, ref);
                            list2.set(2, type);
                            codeSecondPart.add(list2);
                            codeSecondPartMap.put(address, ref);
                        }
                    }

                }
                location = addTwoHexadecimal(location, "3");
                if (isFormatOne) {
                    j += 2;
                } else {
                    j += 6;
                }
            }
        }
    }

    public void processERecord() {
        ArrayList<String> list = new ArrayList<>();
        prepList(list);
        list.set(0, endingAddress);
        list.set(2, "END");
        list.set(3, startingAddress);
        codeSecondPart.add(list);
    }

    public void initializeMaps() {
        //format 3/4 j+=6
        instructionOPCodeMap.put("18", "ADD");
        instructionOPCodeMap.put("40", "AND");
        instructionOPCodeMap.put("28", "COMP");
        instructionOPCodeMap.put("24", "DIV");
        instructionOPCodeMap.put("3C", "J");
        instructionOPCodeMap.put("30", "JEQ");
        instructionOPCodeMap.put("34", "JGT");
        instructionOPCodeMap.put("38", "JLT");
        instructionOPCodeMap.put("48", "JSUB");
        instructionOPCodeMap.put("00", "LDA");
        instructionOPCodeMap.put("50", "LDCH");
        instructionOPCodeMap.put("08", "LDL");
        instructionOPCodeMap.put("04", "LDX");
        instructionOPCodeMap.put("20", "MUL");
        instructionOPCodeMap.put("44", "OR");
        instructionOPCodeMap.put("D8", "RD");
        instructionOPCodeMap.put("4C", "RSUB");
        instructionOPCodeMap.put("0C", "STA");
        instructionOPCodeMap.put("54", "STCH");
        instructionOPCodeMap.put("14", "STL");
        instructionOPCodeMap.put("E8", "STSW");
        instructionOPCodeMap.put("10", "STX");
        instructionOPCodeMap.put("1C", "SUB");
        instructionOPCodeMap.put("D0", "TD");
        instructionOPCodeMap.put("2C", "TIX");
        instructionOPCodeMap.put("DC", "WD");

        //format one j+=2
        instructionFormatOneOPCodeMap.put("C4", "FIX");
        instructionFormatOneOPCodeMap.put("C0", "FLOAT");
        instructionFormatOneOPCodeMap.put("F4", "HIO");
        instructionFormatOneOPCodeMap.put("C8", "NORM");
        instructionFormatOneOPCodeMap.put("F0", "SIO");
        instructionFormatOneOPCodeMap.put("F8", "TIO");

        instructionVariableTypeMap.put("18", "word");
        instructionVariableTypeMap.put("40", "word");
        instructionVariableTypeMap.put("28", "word");
        instructionVariableTypeMap.put("24", "word");
        instructionVariableTypeMap.put("3C", "");
        instructionVariableTypeMap.put("30", "");
        instructionVariableTypeMap.put("34", "");
        instructionVariableTypeMap.put("38", "");
        instructionVariableTypeMap.put("48", "");
        instructionVariableTypeMap.put("00", "word");
        instructionVariableTypeMap.put("50", "byte");
        instructionVariableTypeMap.put("08", "word");
        instructionVariableTypeMap.put("04", "word");
        instructionVariableTypeMap.put("20", "word");
        instructionVariableTypeMap.put("44", "word");
        instructionVariableTypeMap.put("D8", "word");
        instructionVariableTypeMap.put("4C", ""); // RSUB has no operand -> linkage register
        instructionVariableTypeMap.put("0C", "resw");
        instructionVariableTypeMap.put("54", "resb");
        instructionVariableTypeMap.put("14", "resw");
        instructionVariableTypeMap.put("E8", "resw");
        instructionVariableTypeMap.put("10", "resw");
        instructionVariableTypeMap.put("1C", "word");
        instructionVariableTypeMap.put("D0", "word");
        instructionVariableTypeMap.put("2C", "word");
        instructionVariableTypeMap.put("DC", "resw");
        instructionVariableTypeMap.put("C4", "");
        instructionVariableTypeMap.put("C0", "");
        instructionVariableTypeMap.put("F4", "");
        instructionVariableTypeMap.put("C8", "");
        instructionVariableTypeMap.put("F0", "");
        instructionVariableTypeMap.put("F8", "");


    }

    public String addCommaX(String input) {
        StringBuilder stringbuilder = new StringBuilder(input);
        stringbuilder.append(",x");
        return stringbuilder.toString();
    }

    public boolean isCommaXInstruction(String instruction) {
        String inputToProcess = cutFirstTwoCharacters(instruction); // remove opcode -> 4 hexadecimal digits(address)
        String binary = hexadecimalToBinary(inputToProcess);
        if (binary.charAt(0) == '1')
            return true;
        return false;
    }

    public String getCommaXAddress(String instruction) {
        //works with instruction or just address
        String toProcess = instruction;
        if (instruction.length() > 4)
            toProcess = cutFirstTwoCharacters(toProcess);
        toProcess = hexadecimalToBinary(toProcess);
        StringBuilder stringbuilder = new StringBuilder(toProcess);
        stringbuilder.setCharAt(0, '0');
        String output = binaryToHexadecimal(stringbuilder.toString());
        return output;
    }

    public String getImmediateInstruction(String instruction) {
        String opCode = instruction.substring(0, 2);
        String binaryOpCode = hexadecimalToBinary(opCode);
        StringBuilder stringBuilder = new StringBuilder(binaryOpCode);
        stringBuilder.setCharAt(7, '0');
        String readyOPCode = binaryToHexadecimal(stringBuilder.toString());
        String instructionName = instructionOPCodeMap.get(readyOPCode);
        StringBuilder stringBuilder1 = new StringBuilder(instructionName);
        stringBuilder1.append(",Imm");
        return stringBuilder1.toString();
    }


    public boolean isImmediateInstruction(String instruction) {
        String binary = hexadecimalToBinary(instruction);
        if (binary.charAt(7) == '1')
            return true;
        return false;
    }

    public String cutFirstTwoCharacters(String input) {
        //substring doesn't modify the original string it returns new a string
        if (input.length() <= 2)
            return "";
        return input.substring(2);
    }

    public String getAndIncrementReference() {
        StringBuilder stringBuilder = new StringBuilder(reference);
        stringBuilder.append(Integer.toString(referenceNumber));
        referenceNumber += 1;
        return stringBuilder.toString();
    }

    public String hexadecimalToBinary(String input) {
        HashMap<Character, String> map = new HashMap<>();
        map.put('0', "0000");
        map.put('1', "0001");
        map.put('2', "0010");
        map.put('3', "0011");
        map.put('4', "0100");
        map.put('5', "0101");
        map.put('6', "0110");
        map.put('7', "0111");
        map.put('8', "1000");
        map.put('9', "1001");
        map.put('A', "1010");
        map.put('B', "1011");
        map.put('C', "1100");
        map.put('D', "1101");
        map.put('E', "1110");
        map.put('F', "1111");
        map.put('a', "1010");
        map.put('b', "1011");
        map.put('c', "1100");
        map.put('d', "1101");
        map.put('e', "1110");
        map.put('f', "1111");
        StringBuilder stringbuilder = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            String value = map.get(current);
            stringbuilder.append(value);
        }
        return stringbuilder.toString();
    }

    public String binaryToHexadecimal(String input) {
        StringBuilder stringbuilder = new StringBuilder();
        if (input.length() % 4 == 3) {
            stringbuilder.append("0");
        } else if (input.length() % 4 == 2) {
            stringbuilder.append("00");
        } else if (input.length() % 4 == 1) {
            stringbuilder.append("000");
        }
        stringbuilder.append(input);
        String inputToProcess = stringbuilder.toString();
        StringBuilder output = new StringBuilder();
        HashMap<String, Character> map = new HashMap<>();
        map.put("0000", '0');
        map.put("0001", '1');
        map.put("0010", '2');
        map.put("0011", '3');
        map.put("0100", '4');
        map.put("0101", '5');
        map.put("0110", '6');
        map.put("0111", '7');
        map.put("1000", '8');
        map.put("1001", '9');
        map.put("1010", 'A');
        map.put("1011", 'B');
        map.put("1100", 'C');
        map.put("1101", 'D');
        map.put("1110", 'E');
        map.put("1111", 'F');
        for (int i = 0; i < inputToProcess.length(); i += 4) {
            String current = inputToProcess.substring(i, i + 4);//exclusive for the second parameter
            char part = map.get(current);
            output.append(part);
        }
        return output.toString();
    }

    public int hexadecimalToDecimal(String input) {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("A", 10);
        map.put("B", 11);
        map.put("C", 12);
        map.put("D", 13);
        map.put("E", 14);
        map.put("F", 15);
        map.put("a", 10);
        map.put("b", 11);
        map.put("c", 12);
        map.put("d", 13);
        map.put("e", 14);
        map.put("f", 15);
        int exponent = input.length() - 1;
        int coefficient;
        int result = 0;
        for (int i = 0; i < input.length(); i++) {
            String current = String.valueOf(input.charAt(i));
            if (current.equals("a") || current.equals("b") || current.equals("c") || current.equals("d") || current.equals("e") || current.equals("f") || current.equals("A") || current.equals("B") || current.equals("C") || current.equals("D") || current.equals("E") || current.equals("F")) {
                coefficient = map.get(current);
            } else {
                coefficient = Integer.parseInt(current);
            }
            result += coefficient * Math.pow(16, exponent);
            exponent--;
        }
        return result;
    }

    public String decimalToHexadecimal(int input) {
        return Integer.toHexString(input);
    }

    public String addTwoHexadecimal(String input1, String input2) {
        int decimal1 = hexadecimalToDecimal(input1);
        int decimal2 = hexadecimalToDecimal(input2);
        int sum = decimal1 + decimal2;
        String sumString = decimalToHexadecimal(sum);
        StringBuilder stringBuilder = new StringBuilder();
        //make it 4 characters
        if (sumString.length() % 4 == 1) {
            stringBuilder.append("000");
        } else if (sumString.length() == 2) {
            stringBuilder.append("00");
        } else if (sumString.length() == 3) {
            stringBuilder.append("0");
        }
        stringBuilder.append(sumString);

        return stringBuilder.toString();
    }

    public String subtractTwoHexadecimal(String input1, String input2) {
        int decimal1 = hexadecimalToDecimal(input1);
        int decimal2 = hexadecimalToDecimal(input2);
        int sum = decimal1 - decimal2;
        String sumString = decimalToHexadecimal(sum);
        StringBuilder stringBuilder = new StringBuilder();
        //make it 4 characters
        if (sumString.length() % 4 == 1) {
            stringBuilder.append("000");
        } else if (sumString.length() == 2) {
            stringBuilder.append("00");
        } else if (sumString.length() == 3) {
            stringBuilder.append("0");
        }
        stringBuilder.append(sumString);

        return stringBuilder.toString();
    }

    public String subtractTwoHexadecimalAndDivideBy3(String input1, String input2) {
        int decimal1 = hexadecimalToDecimal(input1);
        int decimal2 = hexadecimalToDecimal(input2);
        int sum = (decimal1 - decimal2) / 3;
        String sumString = decimalToHexadecimal(sum);
        StringBuilder stringBuilder = new StringBuilder();
        //make it 4 characters
        if (sumString.length() % 4 == 1) {
            stringBuilder.append("000");
        } else if (sumString.length() == 2) {
            stringBuilder.append("00");
        } else if (sumString.length() == 3) {
            stringBuilder.append("0");
        }
        stringBuilder.append(sumString);

        return stringBuilder.toString();
    }

    public int findRowUsingLocation(String location) {
        for (int i = 0; i < codeFirstPart.size(); i++) {
            ArrayList<String> current = codeFirstPart.get(i);
            for (int j = 0; j < current.size(); j++) {
                if (current.get(0).equals(location)) {
                    return i;
                }
            }
        }
        return 0;
    }


    public void prepList(ArrayList<String> list) {
        //avoid out-of-bounds exception
        for (int i = 0; i < 5; i++) {
            list.add("_______");
        }
    }

    public void print2DArrayList() {
        for (ArrayList<String> innerList : codeFirstPart) {

            for (int i = 0; i < innerList.size(); i++) {
                System.out.print(innerList.get(i) + " ");
            }
            System.out.println();
        }
        for (ArrayList<String> innerList : codeSecondPart) {
            for (int i = 0; i < innerList.size(); i++) {
                System.out.print(innerList.get(i) + " ");
            }
            System.out.println();
        }
        System.out.println(symbolTable);


    }

    public void writeToFile(){
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(outputFile)))) {
            // Loop through the rows
            for (ArrayList<String> row : codeFirstPart) {
                for (String element : row) {
                    writer.print(element + "\t");
                }
                writer.println();
            }
            for (ArrayList<String> row : codeSecondPart) {
                for (String element : row) {
                    writer.print(element + "\t");
                }
                writer.println();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public void writeSymbolTable(){
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(symbolTableFile)))) {
            // Loop through the elements in the ArrayList
            int counter = 0;
            for (String element : symbolTable) {
                // Write each element to the file
                writer.print(element + "\t");
                if (counter==1){
                    writer.println();
                    counter=0;
                    continue;
                }
                counter++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
