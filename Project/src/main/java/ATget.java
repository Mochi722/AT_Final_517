import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;

import java.io.*;
import java.util.*;

public class ATget {
    static Map<String, ArrayList<String>> classANDmethods = new HashMap<String, ArrayList<String>>();  //存储每个class和它所包含的methods
    static ArrayList<String> addList = new ArrayList<String>();                     //全局变量，用于存放需要添加进value的name

    public static void main(String[] args) throws IOException, ClassHierarchyException, IllegalArgumentException, InvalidClassFileException, CancelException {
        Map<String, ArrayList<String>> class_cfa = new HashMap<String, ArrayList<String>>();           //类粒度的调用图
        Map<String, ArrayList<String>> method_cfa = new HashMap<String, ArrayList<String>>();          //方法粒度的调用图
        ArrayList<String> selected_class = new ArrayList<String>();


        //读取分析粒度
        char cORm = args[0].charAt(1);
        String testType = (cORm == 'c') ? "class" : "method";

        //读取target_path
        String target_path = args[1];


        //读取的changeInfo
        ArrayList<String> changeInfos = new ArrayList<String>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(args[2]));
            String str;
            while ((str = in.readLine()) != null) {
                changeInfos.add(str);
            }
        } catch (IOException e) {
            System.out.println(">>>>>>> 读取changeInfo文件失败！<<<<<<<");
        }

        //for idea test
        //String testType="class";
        //String target_path="I:\\大三上\\自动化测试\\ATfinal\\AT_Final_517\\Data\\ClassicAutomatedTesting\\ClassicAutomatedTesting\\0-CMD\\target";

        int j = target_path.length() - 1;
        while (target_path.charAt(j) != '-') {
            j--;
        }
        String projectName = target_path.substring(j + 1, target_path.length() - 7);

        ClassLoader classLoader = ATget.class.getClassLoader();
        AnalysisScope scope = AnalysisScopeReader.readJavaScope("scope.txt", new FileProvider().getFile("exclusion.txt"), classLoader);
        loadFileToScope(target_path, scope);
        System.out.println(">>>>>>> 生成分析域完成 <<<<<<<");
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
        Iterable<Entrypoint> eps = new AllApplicationEntrypoints(scope, cha);  //
        CHACallGraph cg = new CHACallGraph(cha);
        cg.init(eps);

        System.out.println(">>>>>>> 开始遍历调用关系，请稍等 <<<<<<<");
        for (CGNode node : cg) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    String signature = method.getSignature();

                    if (!classANDmethods.containsKey(classInnerName)) {
                        classANDmethods.put(classInnerName, new ArrayList<String>());
                    }
                    if (!classANDmethods.get(classInnerName).contains(signature)) {
                        classANDmethods.get(classInnerName).add(signature);
                    }
                    if (!class_cfa.containsKey(classInnerName)) {
                        class_cfa.put(classInnerName, new ArrayList<String>());
                    }
                    if (!method_cfa.containsKey(method_cfa)) {
                        method_cfa.put(signature, new ArrayList<String>());
                    }

                    for (Iterator<CGNode> it = cg.getPredNodes(node); it.hasNext(); ) {
                        CGNode preNode = it.next();
                        String preNode_cname = preNode.getMethod().getDeclaringClass().getName().toString();  //前继节点所属的类名
                        String preNode_signature = preNode.getMethod().getSignature();                        //前继节点的方法签名
                        if ("Application".equals(preNode.getMethod().getDeclaringClass().getClassLoader().toString())) {

                            //构建类粒度的调用图
                            if (!class_cfa.get(classInnerName).contains(preNode_cname)) {
                                class_cfa.get(classInnerName).add(preNode_cname);
                            }

                            //构建方法粒度的调用图（only直接调用）
                            if (!method_cfa.get(signature).contains(preNode_signature)) {
                                method_cfa.get(signature).add(preNode_signature);
                            }
                        }
                    }
                    if (class_cfa.get(classInnerName).size() == 0) {
                        class_cfa.get(classInnerName).add("null");
                    }
                    if (method_cfa.get(signature).size() == 0) {
                        method_cfa.get(signature).add("null");
                    }
                }
            } else {
                //System.out.println(String.format("'%s'不是一个ShrikeBTMethod：%s", node.getMethod(), node.getMethod().getClass()));
            }
        }

        System.out.println(">>>>>>> 遍历完成 <<<<<<<");
        //需要寻找所有间接调用
        method_cfa = getCompleteCFA(method_cfa);
        class_cfa = getCompleteCFA(class_cfa);
        //根据完整调用图生成dot文件
        createDOT(class_cfa, "class", projectName);
        createDOT(method_cfa, "method", projectName);


        //从changeInfos中逐条读取变化的方法or类，并调用getSelected方法选择测试，将结果存入selected_class中
        System.out.println(">>>>>>> 开始选择测试 <<<<<<<");
        for (String changed : changeInfos) {
            int i = 0;
            while (changed.charAt(i) != ' ') {
                i++;
            }

            if (testType.equals("class")) {
                addSpec(selected_class, getSelected(changed.substring(0, i), class_cfa, testType));
            } else {
                addSpec(selected_class, getSelected(changed.substring(i + 1), method_cfa, testType));
            }
        }
        //根据selected_class选择的测试生成结果文件
        createTXT(selected_class, testType, projectName);
        System.out.println("\n>>>>>>> 测试选择结果如下 <<<<<<<");
        printOut(selected_class);
    }

    /**
     * 本方法将path路径下所有的.class文件加载进scope中
     *
     * @param path  需要检索读取的文件目录路径
     * @param scope 需要添加classFile的scope
     */
    public static void loadFileToScope(String path, AnalysisScope scope) throws FileNotFoundException, IOException, InvalidClassFileException {
        try {
            File file = new File(path);
            if (!file.isDirectory()) {
                scope.addClassFileToScope(ClassLoaderReference.Application, file);
            } else if (file.isDirectory()) {
                String[] fileList = file.list();
                for (int i = 0; i < fileList.length; i++) {
                    File readfile = new File(path + "\\" + fileList[i]);
                    if (!readfile.isDirectory()) {
                        if (fileList[i].contains(".class")) {
                            scope.addClassFileToScope(ClassLoaderReference.Application, readfile);
                        }
                    } else if (readfile.isDirectory()) {
                        loadFileToScope(path + "\\" + fileList[i], scope);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("loadFileToScope()   Exception:" + e.getMessage());
        }
    }


    /**
     * getCompleteCFA用于获取完整的调用关系
     *
     * @param cfa 传入自存图结构
     * @return 添加所有直/间接调用关系后的完整图
     */
    public static Map<String, ArrayList<String>> getCompleteCFA(Map<String, ArrayList<String>> cfa) {
        for (String key : cfa.keySet()) {
            ArrayList<String> preds = cfa.get(key);
            addList.clear();
            for (String item : preds) {
                if (item.equals("null")) {
                    break;
                }
                getAllPred(item, cfa);
            }
            //接下来把addlist中不属于preds的部分加入preds
            for (String addItem : addList) {
                if (!preds.contains(addItem)) {
                    preds.add(addItem);
                }
            }
        }
        return cfa;
    }


    /**
     * getAllPred递归获取所有间接调用
     *
     * @param cfa  传入自存图结构
     * @param name 获取key为name的节点的所有直接间接前继节点
     */
    public static void getAllPred(String name, Map<String, ArrayList<String>> cfa) {
        ArrayList<String> nameList = cfa.get(name);
        if (addList.contains(name)) {
            return;
        }
        addList.add(name);
        for (String item : nameList) {
            if (item.equals("null")) {
                break;
            }
            getAllPred(item, cfa);
        }
    }


    /**
     * createDOT用于将cfa中的调用关系转换写入dot文件
     *
     * @param cfa         提供所有调用关系的cfa
     * @param type        分为class和method两种
     * @param projectName 被测试的项目的名称
     */
    public static void createDOT(Map<String, ArrayList<String>> cfa, String type, String projectName) {
        try {
            String fileName = type + "-" + projectName + "-cfa.dot";
            BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
            out.write("digraph " + projectName + "_" + type + " {\n");
            for (String key : cfa.keySet()) {
                ArrayList<String> values = cfa.get(key);
                for (String item : values) {
                    if (item.equals("null")) {
                        continue;
                    }
                    out.write("    \"" + key + "\" -> \"" + item + "\";\n");
                }
            }
            out.write("}\n");
            out.close();
            System.out.println(">>>>>>> " + type + "-" + projectName + "-cfa.dot文件创建成功！<<<<<<<");
            drawPDF(projectName, type);   //dot文件创建成功后，在CMD执行dot命令绘图
        } catch (IOException e) {
            System.out.println(">>>>>>> ERROR! dot文件创建失败！<<<<<<<");
        }
    }


    /**
     * 本方法用于创建selected-method.txt和selected-class.txt
     */
    public static void createTXT(ArrayList<String> selected, String testType, String projectName) {
        try {
            String fileName = projectName + "-selection-" + testType + ".txt";
            BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
            for (String item : selected) {
                out.write(item + "\n");
            }
            out.close();
            System.out.println(">>>>>>> " + projectName + "-selection-" + testType + ".txt文件创建成功！<<<<<<<");
        } catch (IOException e) {
            System.out.println(">>>>>>> ERROR! txt文件创建失败！<<<<<<<");
        }
    }

    /**
     * getSelected方法用于寻找某class/method变化后需要变化的test
     *
     * @param changed 变化的class/method的名称
     * @param cfa     完整调用图
     * @param type    选择的粒度类型，有"class"和"method"两种
     */
    public static ArrayList<String> getSelected(String changed, Map<String, ArrayList<String>> cfa, String type) {
        ArrayList<String> values = cfa.get(changed);
        ArrayList<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.contains("Test")) {
                //如果是class，去map里遍历所有method把签名加在后面，存入result
                if (type.equals("class")) {
                    ArrayList<String> methods = classANDmethods.get(value);
                    for (String sig : methods) {
                        if (!sig.contains("init")) {
                            result.add(value + " " + sig);
                        }
                    }
                } else {//如果是method，去map里找到class，加在前面，存入result
                    for (String cls : classANDmethods.keySet()) {
                        if (classANDmethods.get(cls).contains(value) && !value.contains("init")) {
                            result.add(cls + " " + value);
                        }
                    }
                }
            }
        }
        return result;
    }


    /**
     * 本方法实现target=target.addAll(adder),同时保证无冗余项
     * @param target 添加后的结果
     * @param adder 待添加的项
     */
    public static void addSpec(ArrayList<String> target, ArrayList<String> adder) {
        for (String item : adder) {
            if (!target.contains(item)) {
                target.add(item);
            }
        }

    }

    //逐条输出list的内容
    public static void printOut(ArrayList<String> list) {
        for (String item : list) {
            System.out.println(item);
        }
    }


    /**
     * 本方法通过执行dot -T pdf -o <文件名>.pdf <文件名>.dot命令生成调用图.pdf
     * */
    public static void drawPDF(String projectName, String type) {
        String fileName = type + "-" + projectName + "-cfa";
        Runtime r = Runtime.getRuntime();
        Process p = null;
        try {
            String command = "dot -T pdf -o " + fileName + ".pdf " + fileName + ".dot";
            p = r.exec(command);
            System.out.println(">>>>>>> 生成依赖图" + fileName + ".pdf成功！<<<<<<<");
        } catch (Exception e) {
            System.out.println(">>>>>>> 生成依赖图失败！<<<<<<<");
        }
    }
}


