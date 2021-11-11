import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
	
	static final int TYPE_PNG = 0;
	static final int TYPE_BORDER_CROP = 1;
	static final int TYPE_ART_CROP = 2;
	static final int TYPE_LARGE = 3;
	static final int TYPE_NORMAL = 4;
	static final int TYPE_SMALL = 5;

    static long smallest = Long.MAX_VALUE;
    static String smallestFile = "";
    static boolean checkExist = false;
    static boolean checkLocal = false;
    static boolean checkRemote = true;
    static boolean useProxy = true;
    static int image_type = TYPE_LARGE;

    static class DownloadThread implements Runnable {
        public boolean running = false;
        public String set = "";

        public boolean checkFile(File file, String urlStr) {
            boolean ret = true;

            if (checkLocal) {
                try {
                    byte buffer[] = new byte[1024 * 1024 * 4];
                    InputStream fis = new FileInputStream(file);
                    BufferedInputStream bis = new BufferedInputStream(fis, buffer.length);
                    int readSize = 0;
                    byte b1 = 0, b2 = 0, b3 = 0, b4 = 0;
                    while ((readSize = bis.read(buffer)) != -1) {
                        b4 = buffer[readSize - 4];
                        b3 = buffer[readSize - 3];
                        b2 = buffer[readSize - 2];
                        b1 = buffer[readSize - 1];
                    }
                    if (file.getAbsolutePath().toLowerCase().endsWith(".jpg")) {
                        if (!(b2 == -1 && b1 == -39)) {
                            System.err.println(String.format("Check Binary Error: %s %02X %02X [FF D9]",
                                    file.getAbsolutePath(), b2, b1));
                            ret = false;
                        }
                    } else if (file.getAbsolutePath().toLowerCase().endsWith(".png")) {
                        if (!(b4 == -82 && b3 == 66 && b2 == 96 && b1 == -126)) {
                            System.err.println(String.format("Check Binary Error: %s %02X %02X %02X %02X [AE 42 60 82]",
                                    file.getAbsolutePath(), b4, b3, b2, b1));
                            ret = false;
                        }
                    }
                    bis.close();
                    fis.close();
                } catch (Exception e) {
                    e.printStackTrace();
                	ret = false;
                }
            }

            if (checkRemote) {
                try {
                	Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080));
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection(useProxy ? proxy : Proxy.NO_PROXY);
                    conn.setConnectTimeout(3 * 1000);
                    conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
                    int length = conn.getContentLength();
                    if (length > 0 && length != file.length()) {
                        System.err.println(String.format("Check Length Error: %s local:%d remote:%d",
                                file.getAbsolutePath(), (int) file.length(), length));
                        ret = false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                	ret = false;
                }
            }

            return ret;
        }

        public boolean downLoadFromUrl(String urlStr, String fileName, String savePath) throws IOException {

            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                saveDir.mkdir();
            }
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }
            
            File file = new File(saveDir + File.separator + fileName);

            if (checkExist || checkLocal || checkRemote) {
            	if(!file.exists()) {
            		if((checkExist || checkLocal) && fileName.contains("_b.")) {
                		return false;
                	}
            	} else {
                    boolean pass = checkFile(file, urlStr);
                    if (pass) {
                        if (file.length() < smallest) {
                            smallest = file.length();
                            smallestFile = file.getAbsolutePath();
                        }
                        return true;
                    } else {
                        file.delete();
                    }
            	}
            }

            URL url = new URL(urlStr);
            byte[] getData = null;
            int retry = 0;

            while (retry <= 3) {
            	Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(useProxy ? proxy : Proxy.NO_PROXY);
                conn.setConnectTimeout(3 * 1000);
                conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
                int length = conn.getContentLength();
                
                if(conn.getResponseCode() == 404)
                	return false;

                InputStream inputStream = conn.getInputStream();
                getData = readInputStream(inputStream);
                if (inputStream != null) {
                    inputStream.close();
                }

                if (getData.length == length) {
                    break;
                }

                System.err.println("Error: Length! " + url + " " + getData.length + " " + length);

                retry++;
                if (retry > 10) {
                    break;
                }
            }

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(getData);
            if (fos != null) {
                fos.close();
            }

            System.out.println("Info: " + url + " -> " + file + " download success");
            
            return true;
        }

        byte[] readInputStream(InputStream inputStream) throws IOException {
            byte[] buffer = new byte[1024];
            int len = 0;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            while ((len = inputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            bos.close();
            return bos.toByteArray();
        }

        String readUrl(String http) {
            String content = "";
            try {
            	Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080));
                URL url = new URL(http);
                URLConnection URLconnection = url.openConnection(useProxy ? proxy : Proxy.NO_PROXY);
                URLconnection.setRequestProperty("User-Agent",
                        "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
                URLconnection.setConnectTimeout(60000);
                URLconnection.setReadTimeout(60000);
                HttpURLConnection httpConnection = (HttpURLConnection) URLconnection;
                int responseCode = httpConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream in = httpConnection.getInputStream();
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader bufr = new BufferedReader(isr);
                    String str;
                    while ((str = bufr.readLine()) != null) {
                        content += str + "\n";
                    }
                    bufr.close();
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return "";
                } else if (responseCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                    return null;
                } else {
                    System.err.println("Error " + responseCode + " : " + url);
                }
            } catch (Exception e) {
                return null;
            }
            return content;
        }

        String tryUrl(String http) {
            String content = null;
            int retry = 0;
            while (content == null) {
                content = readUrl(http);
                if (retry > 10) {
                    System.err.println("Error Retry: " + http);
                    return "";
                }
                retry++;
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return content;
        }

        String getNewName(String s) {
            Pattern p = Pattern.compile("[^\\d]*(\\d+)[^\\d]*");
            Matcher m = p.matcher(s);
            if (m.find()) {
                String num = m.group(1);
                if (num.length() == 2 && !num.equals("00")) {
                    return s.replace(num, "0" + num);
                } else if (num.length() == 1) {
                	return s.replace(num, "00" + num);
                }
            }
            return s;
        }
        
        boolean checkFilter(String card, String set, String index) {
        	if(card.endsWith("/invoke-prejudice"))
        		return true;
        	else if(card.endsWith("/cleanse"))
        		return true;
        	else if(card.endsWith("/stone-throwing-devils"))
        		return true;
        	else if(card.endsWith("/pradesh-gypsies") || card.endsWith("/z%C3%ADngaros-de-pradesh") || card.endsWith("/boh%C3%A9miens-pradeshi"))
        		return true;
        	else if(card.endsWith("/jihad"))
        		return true;
        	else if(card.endsWith("/imprison"))
        		return true;
        	else if(card.endsWith("/crusade") || card.endsWith("/cruzada") || card.endsWith("/croisade"))
        		return true;
        	if((set.equals("amh2") || set.equals("astx")) && index.contains("s"))
        		return true;
        	return false;
        }

        public void downloadSet(String set) {
            String url = "https://scryfall.com/sets/" + set + "?as=checklist";
            String content = tryUrl(url);
            int cards = 0;

            Pattern pattern = Pattern.compile("(\\d+)\\&nbsp;cards");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
            	cards = Integer.parseInt(matcher.group(1));
            }
            
            int num = 0;
            int skipped = 0;

            Vector<String> index_vector = new Vector<>();
            pattern = Pattern.compile("<a tabindex=\"-1\" href=\"(/card/[^\"]+)\">([^< ]*\\d+[^<]*)</a>");
            matcher = pattern.matcher(content);
            while (matcher.find()) {
            	String s = matcher.group(2).replace("*", "★").replace("†", "☆");
                if (!s.contains("&#")) {
                	if(!checkFilter(matcher.group(1), set, s)) {
                    	if(!index_vector.contains(s)) {
                    		index_vector.add(s);
                    	} else {
                    		System.err.println("Duplicate index? " + set + " " + s);
                    	}
                	} else if(!set.equals("amh2") && !set.equals("astx")) {
                		skipped++;
                	}
                }
            }

            String folder = "E:/Scryfall/";
            switch(image_type) {
            case TYPE_PNG:
            	folder += "png/";
            	break;
            case TYPE_BORDER_CROP:
            	folder += "border_crop/";
            	break;
            case TYPE_ART_CROP:
            	folder += "art_crop/";
            	break;
            case TYPE_LARGE:
            	folder += "large/";
            	break;
            case TYPE_NORMAL:
            	folder += "normal/";
            	break;
            case TYPE_SMALL:
            	folder += "small/";
            	break;
            }
            
            Vector<String> image_vector = new Vector<>();
            pattern = Pattern.compile("(https://c1.scryfall.com/file/scryfall-cards/large/front/././(.*?)\\.jpg\\?\\d+)");
            matcher = pattern.matcher(content);

            int i = 0;
            while (matcher.find()) {
                String s = matcher.group(1);
                
                if (!image_vector.contains(s)) {
                	image_vector.add(s);
                    switch(image_type) {
                    case TYPE_PNG:
                    	s = s.replace("/large/", "/png/").replace(".jpg", ".png");
                    	break;
                    case TYPE_BORDER_CROP:
                    	s = s.replace("/large/", "/border_crop/");
                    	break;
                    case TYPE_ART_CROP:
                    	s = s.replace("/large/", "/art_crop/");
                    	break;
                    case TYPE_NORMAL:
                    	s = s.replace("/large/", "/normal/");
                    	break;
                    case TYPE_SMALL:
                    	s = s.replace("/large/", "/small/");
                    	break;
                    }
                    
                    int retry = 0;
                    String index = "Unknown_" + (i + 1);
                    if(i < index_vector.size()) {
                    	index = index_vector.get(i);
                    } else {
                    	System.err.println("Index error! " + set + " " + index + " " + s);
                    }
                    while(true) {
                        try {
                        	String suffix = image_type == TYPE_PNG ? ".png" : ".jpg";
                        	boolean success = downLoadFromUrl(s.replace("/front/", "/back/"), getNewName(index + "_b" + suffix),
                        			folder + (set.equals("con") ? "CFX" : set.toUpperCase()));
                            downLoadFromUrl(s, getNewName(index + (success ? "_a": "") + suffix),
                            		folder + (set.equals("con") ? "CFX" : set.toUpperCase()));
                            if(success)
                            	num += 2;
                            else
                            	num++;
                            if(retry > 5) {
                            	System.out.println("Retry success! " + set + " " + index + " " + s);
                            }
                            break;
                        } catch (IOException e) {
                        	if(retry >= 5) {
                        		System.out.println(set + " " + index + " " + s + " Retry " + (retry + 1));
                        	}
                            retry++;
                            try {
								Thread.sleep(5000);
							} catch (InterruptedException e1) {
							}
                            if(retry > 10) {
                            	System.err.println("Error reach MAX! " + set + " " + index + " " + s);
                            	break;
                            }
                        }
                    }

                    i++;
                }
            }

            if(index_vector.size() != image_vector.size()) {
            	System.err.println("Index mismatch: " + set + " index " + index_vector.size() + " vs. image " + image_vector.size());
            }

            File set_folder = new File(folder + (set.equals("con") ? "CFX" : set.toUpperCase()));
            int folder_card = 0;
            for(String s : set_folder.list()) {
            	if(!s.contains("back.")) {
            		folder_card++;
            	}
            }
            if(folder_card != num + skipped) {
            	System.err.println("Card number: " + set + " folder " + folder_card + " vs. num " + num + " + skipped " + skipped);
            }

            System.out.println("Finished " + set + " " + cards + " cards");
            running = false;
        }

        @Override
        public void run() {
            downloadSet(set);
        }
    }

    static String[] sets = new String[] {
    		/*"khm", "stx", "c21", "mh2", "afr", "eld", "thb", "und", "c20", "iko", "m21", "jmp", "znr", "cmr", "2xm", "gn2",
    		"teld", "tgn2", "tthb", "tund", "tc20", "tiko", "tm21", "t2xm", "tznr", "znc", "tznc", "tcmr", "tkhm", "khc",
    		 "tkhc", "tstx", "tc21", "tmh2", "tafr", "afc", "tafc"*/
    		"htr20","afr","pafr","tafr","afc","tafc","oafc","aafr","mafr","plg21","mh2","pmh2","tmh2","mmh2","amh2","h1r",
    		"pwp21","ha5","stx","sta","pstx","tstx","mstx","astx","c21","tc21","oc21","tsr","ttsr","ha4","khm","pkhm","tkhm",
    		"mkhm","khc","tkhc","akhm","pl21","pj21","cc1","cmr","tcmr","klr","plist","znr","zne","pznr","tznr","sznr","mznr",
    		"aznr","znc","tznc","anb","akr","2xm","t2xm","htr19","jmp","ajmp","fjmp","m21","pm21","tm21","ss3","ha3","plg20",
    		"iko","piko","tiko","c20","tc20","oc20","ha2","und","tund","thb","pthb","tthb","pf20","j20","sld","slu","ha1",
    		"gn2","tgn2","mb1","cmb2","fmb1","cmb1","ptg","eld","peld","teld","c19","tc19","oc19","htr18","ps19","m20","pm20",
    		"tm20","ppp1","ss2","mh1","pmh1","tmh1","amh1","war","pwar","twar","j19","gk2","tgk2","rna","prna","trna","prw2",
    		"pf19","uma","puma","tuma","gnt","gk1","tgk1","grn","pgrn","tgrn","prwk","med","tmed","c18","tc18","oc18","htr17",
    		"ps18","ana","xana","oana","pana","m19","pm19","tm19","g18","pss3","gs1","ss1","bbd","pbbd","tbbd","cm2","tcm2",
    		"dom","pdom","tdom","ddu","tddu","a25","ta25","plny","pnat","rix","prix","trix","j18","f18","ust","pust","tust",
    		"e02","v17","ima","tima","ddt","tddt","g17","xln","pxln","txln","pxtc","pss2","h17","htr16","c17","tc17","oc17",
    		"ps17","hou","phou","thou","e01","te01","oe01","cma","tcma","akh","mp2","pakh","takh","w17","dds","tdds","mm3",
    		"tmm3","aer","paer","taer","l17","f17","j17","pca","tpca","opca","pz2","c16","tc16","oc16","ps16","kld","mps",
    		"pkld","tkld","ddr","cn2","tcn2","v16","emn","pemn","temn","ema","tema","w16","soi","psoi","tsoi","ddq","ogw",
    		"pogw","togw","l16","f16","j16","pz1","c15","tc15","oc15","bfz","exp","pbfz","tbfz","pss1","ddp","v15","ori",
    		"pori","tori","cp3","ps15","mm2","tmm2","tpr","dtk","pdtk","tdtk","ptkdf","ddo","frf","pfrf","tfrf","cp2","ugin",
    		"l15","j15","f15","evg","tevg","dvd","tdvd","jvc","tjvc","gvl","tgvl","c14","tc14","oc14","ktk","pktk","tktk","ddn",
    		"v14","m15","pm15","tm15","cp1","ppc1","ps14","vma","cns","pcns","tcns","md1","tmd1","jou","pjou","tjou","tdag",
    		"thp3","ddm","tddm","bng","pbng","tbng","tbth","thp2","pdp15","pi14","l14","j14","f14","c13","oc13","ths","pths",
    		"tths","tfth","thp1","ddl","tddl","v13","m14","pm14","tm14","psdc","mma","tmma","dgm","pdgm","tdgm","pwcq","ddk",
    		"tddk","gtc","pgtc","tgtc","pdp14","pi13","l13","j13","f13","cm1","ocm1","rtr","prtr","trtr","ddj","tddj","v12",
    		"m13","pm13","tm13","pc2","opc2","avr","pavr","tavr","phel","ddi","tddi","dka","pdka","tdka","pidw","pwp12","pdp13",
    		"l12","f12","j12","pd3","isd","pisd","tisd","ddh","tddh","v11","m12","pm12","tm12","cmd","pcmd","ocmd","td2","nph",
    		"pnph","tnph","ddg","tddg","mbs","pmbs","tmbs","me4","pmps11","pdp12","pwp11","olgc","ps11","p11","g11","f11","pd2",
    		"td0","som","psom","tsom","ddf","tddf","v10","m11","pm11","tm11","arc","oarc","parc","dpa","roe","proe","troe","dde",
    		"tdde","wwk","pwwk","twwk","pdp10","pwp10","pmps10","g10","f10","p10","h09","ddd","tddd","zen","pzen","tzen","me3",
    		"hop","ohop","phop","v09","m10","pm10","tm10","arb","parb","tarb","ddc","tddc","purl","con","pcon","tcon","pbook",
    		"pwp09","pdtp","pmps09","p09","f09","g09","dd2","tdd2","ala","pala","tala","pwpn","me2","drb","eve","peve","teve",
    		"shm","pshm","tshm","p15a","mor","pmor","tmor","pmps08","pg08","p08","f08","g08","dd1","tdd1","lrw","plrw","tlrw",
    		"me1","psum","10e","p10e","t10e","fut","pfut","pgpx","ppro","plc","pplc","pres","pg07","pmps07","f07","p07","g07",
    		"hho","tsp","ptsp","tsb","csp","pcsp","cst","dis","pdis","pcmp","gpt","pgpt","pal06","pmps06","pjas","phuk","p06",
    		"g06","f06","pgtw","p2hg","rav","prav","psal","9ed","p9ed","sok","psok","bok","pbok","pmps","pal05","pjse","f05",
    		"p05","g05","unh","punh","chk","pchk","wc04","5dn","p5dn","dst","pdst","pal04","p04","f04","g04","mrd","pmrd","wc03",
    		"8ed","p8ed","scg","pscg","lgn","plgn","pmoa","pjjt","pal03","f03","ovnt","g03","p03","ons","pons","wc02","phj","prm",
    		"jud","pjud","tor","ptor","pal02","f02","g02","pr2","dkm","ody","pody","wc01","psdg","apc","papc","7ed","pls","ppls",
    		"pal01","mpr","f01","g01","inv","pinv","btd","wc00","pcy","ppcy","s00","nem","pnem","pelp","pal00","fnm","g00","psus",
    		"brb","mmq","pmmq","pwos","wc99","pwor","pgru","ptk","pptk","s99","uds","puds","6ed","ulg","pulg","pal99","g99","ath",
    		"usg","pusg","palp","wc98","ugl","tugl","p02","exo","pexo","sth","psth","jgp","tmp","ptmp","wc97","wth","pvan","por",
    		"ppod","past","pmic","5ed","vis","itp","mgb","mir","pred","pcel","parl","rqs","all","ptc","hml","ren","rin","chr","ice",
    		"4bb","4ed","pmei","plgm","fem","phpr","drk","pdrc","sum","leg","3ed","fbb","atq","arn","cei","ced","2ed","leb","lea"
    		};
    		
    static DownloadThread[] threads = new DownloadThread[8];

    public static void main(String[] args) {
        int index = 0;
        boolean finished = false;
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new DownloadThread();
        }
        while (!finished) {
            int finishedNum = 0;
            for (int i = 0; i < threads.length; i++) {
                if (!threads[i].running) {
                    if (index < sets.length) {
                        threads[i].set = sets[index];
                        index++;
                        System.out.println("Start " + threads[i].set + " " + index + "/" + sets.length);
                        new Thread(threads[i]).start();
                        threads[i].running = true;
                    } else {
                        finishedNum++;
                    }
                }
            }
            if (finishedNum == threads.length) {
                finished = true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (!smallestFile.isEmpty()) {
            System.out.println("\nSmallest: " + smallest + " " + smallestFile);
        }
    }

}
