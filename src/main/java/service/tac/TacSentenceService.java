package service.tac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import mapper.tac.TacSentenceMapper;
import model.tac.TacDocument;
import model.tac.TacSentence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import service.Service;
import service.tac.AMLMain.CountMapper;
import tac.TokenizerUtils;
import utils.MatrixUtils;
import Jama.Matrix;

import com.google.gson.internal.LinkedTreeMap;

public class TacSentenceService extends Service {
	static Logger logger = LoggerFactory.getLogger(TacSentenceService.class);

	private TacSentenceMapper sentenceMapper = session
			.getMapper(TacSentenceMapper.class);

	/**
	 * 计算这一组文档中词的IDF,以句子为单位
	 * 
	 * @param documents
	 * @return
	 */
	public Map<String, Double> compuateLocalIDF(List<TacDocument> documents) {
		Map<String, Double> idfMap = new HashMap<>();
		Map<String, Integer> docCount = new HashMap<>();
		for (TacDocument document : documents) {
			List<TacSentence> sentences = document.getSentences();
			for (TacSentence sentence : sentences) {
				Set<String> wordsSet = new HashSet<>();
				Map<String, Integer> tf = sentence.getTF();
				for (Entry<String, Integer> entry : tf.entrySet()) {
					String word = entry.getKey();
					wordsSet.add(word);
				}
				for (String tmp : wordsSet) {
					Integer c = docCount.get(tmp);
					if (c == null) {
						c = 1;
					} else {
						c++;
					}
					docCount.put(tmp, c);
				}
			}
		}
		logger.info("documents.size: {}", documents.size());
		for (Entry<String, Integer> entry : docCount.entrySet()) {
			String word = entry.getKey();
			int containsCount = entry.getValue();
			double idf = Math.log(documents.size() / containsCount);
			logger.info("word : {}, cotainsCount : {}", word, containsCount);
			idfMap.put(word, idf);
		}
		return idfMap;
	}

	/**
	 * 计算这一组文档中词的IDF,以句子为单位
	 * 
	 * @param documents
	 * @return
	 */
	public Map<String, Double> compuateLocalIDFFromSentences(
			List<TacSentence> sentences) {
		Map<String, Double> idfMap = new HashMap<>();
		Map<String, Integer> docCount = new HashMap<>();
		for (TacSentence sentence : sentences) {
			Set<String> wordsSet = new HashSet<>();
			Map<String, Integer> tf = sentence.getTF();
			for (Entry<String, Integer> entry : tf.entrySet()) {
				String word = entry.getKey();
				wordsSet.add(word);
			}
			for (String tmp : wordsSet) {
				Integer c = docCount.get(tmp);
				if (c == null) {
					c = 1;
				} else {
					c++;
				}
				docCount.put(tmp, c);
			}
		}
		// logger.info("sentences.size: {}", sentences.size());
		for (Entry<String, Integer> entry : docCount.entrySet()) {
			String word = entry.getKey();
			int containsCount = entry.getValue();
			double idf = Math.log(sentences.size() / containsCount);
			// if (containsCount > 10) {
			// logger.info("word : {}, cotainsCount : {}", word, containsCount);
			// }
			idfMap.put(word, idf);
		}
		return idfMap;
	}

	public List<TacSentence> getSentencesSameEvent(String eventName) {
		List<TacSentence> sentences = sentenceMapper.findByEventname(eventName);
		if(sentences != null && sentences.size() > 0){
			for (TacSentence sentence : sentences) {
				String content = sentence.getContent();
				if(content != null && content.length() > 0){
					Map<String, Integer> words = TokenizerUtils.tokenizerMap(content);
					sentence.setTF(words);
				}else{
					logger.error("eventName : {}, some sentence  is null", eventName);
				}
			}
		}
		return sentences;
	}

	public Map<String, List<TacSentence>> groupSentenceByDate(
			List<TacSentence> sentencesSameEvent) {
		Map<String, List<TacSentence>> map = new HashMap<>();
		LinkedList<String> dateList = new LinkedList<>();
		for (TacSentence sentence : sentencesSameEvent) {
			String date = sentence.getPublishDate();
			List<TacSentence> list = map.get(date);
			if (list == null) {
				list = new ArrayList<>();
				dateList.add(date);
			}
			list.add(sentence);
			map.put(date, list);
		}
		Collections.sort(dateList);
		Map<String, List<TacSentence>> newMap = new LinkedTreeMap<>();
		for (String d : dateList) {
			newMap.put(d, map.get(d));
		}
		return newMap;
	}

	public Matrix constructLSAMatrix(List<TacSentence> sentences,
			Map<String, Double> wordsIDF, CountMapper cm) {
		Matrix matrix = constructMatrix(sentences, wordsIDF, cm);
		Matrix lsa = MatrixUtils.lsa(matrix);
		return lsa;
	}
//	private Matrix constructMatrix(List<TacSentence> sentences,
//			Map<String, Double> wordsIDF) {
//		int m = wordsIDF.size();
//		int n = sentences.size();
//		Matrix matrix = new Matrix(n, m);
//		List<String> terms = new ArrayList<String>();
//		terms.addAll(wordsIDF.keySet());
//		for (int i = 0; i < m; i++) {
//			for (int j = 0; j < n; j++) {
//				if (sentences.get(j).getTF().containsKey(terms.get(i))) {
//					matrix.set(j, i, wordsIDF.get(terms.get(i)) * sentences.get(j).getTF().get(terms.get(i)));
//				}
//			}
//		}
////		MatrixUtils.printMatrixUtils(matrix);
//		return matrix;
//	}
	//自己写的方法
	private Matrix constructMatrix(List<TacSentence> sentences,
			Map<String, Double> wordsIDF, CountMapper cm) {
		logger.info("构造 行数：{}， 列数:{} 的TFIDF矩阵", sentences.size(),
				wordsIDF.size());
		Matrix matrix = new Matrix( wordsIDF.size() ,sentences.size()); // 行为词，列为句子
		int sentenceSeq = 0;
		for (TacSentence sentence : sentences) {
			Map<String, Integer> tf = sentence.getTF();
			for (Entry<String, Integer> entry : tf.entrySet()) {
				String word = entry.getKey();
				int seq = cm.getSeqNumber(word);
				int counts = entry.getValue();
				double weight = counts * wordsIDF.get(word);
//				logger.info("col: {}, row : {}", sentenceSeq, seq);
//				logger.info("word: {}, weight : {}", word, weight);
				matrix.set(seq, sentenceSeq, weight);
			}
			sentenceSeq++;
		}
		// MatrixUtils.printMatrixUtils(matrix);
		return matrix;
	}

	

	public List<String> findEvents() {
		return sentenceMapper.findEvents();
	}
	private double alpha = 0.118659;
	private double beta = 0.145198;
	public Map<String, Double> computeEnergeTody(List<TacSentence> sentences, Set<String> words) {
		int[][] senNum1 = statistic(words, sentences);//[0]:句子包含该词的个数，[1]:句子缺的词的个数
		int[][] senNum2 = statistic(words.size(), sentences.size());
		Map<String, Double> initEnergyMap = new LinkedHashMap<String, Double>();
		int i = 0;
		for (String word : words) {
			double termkafang = alpha
					* ((senNum1[i][0] + senNum1[i][1] + senNum2[i][0] + senNum2[i][1]) * Math
							.pow((senNum1[i][0] * senNum2[i][1] - senNum1[i][1]
									* senNum2[i][0]), 2))
					/ ((senNum1[i][0] + senNum1[i][1])
							* (senNum2[i][0] + senNum2[i][1])
							* (senNum1[i][0] + senNum2[i][0]) * (senNum1[i][1] + senNum2[i][1]));
			initEnergyMap.put(word, termkafang / (1 + termkafang));
			i++;
		}
		return initEnergyMap;
	}
	// 统计包含和不包含一个词的句子个数
	public int[][] statistic(Set<String> termSet, List<TacSentence> senList) {
		int[][] senNum = new int[termSet.size()][2];
		int num = 0;
		for(String s : termSet) {
			for (int i = 0; i < senList.size(); i++) {
				if (senList.get(i).getContent().contains(s)) {
					senNum[num][0]++;// sentences number containing the word
				} else
					senNum[num][1]++;// sentences number do not containing the
										// word
			}
			num++;
		}
		return senNum;
	}
	//不知道这算的啥
	public int[][] statistic(int m, int n) {
		int[][] senNum = new int[m][2];
		for (int i = 0; i < m; i++) {
			senNum[i][0] = 0;
			senNum[i][1] = n;
		}
		return senNum;
	}

	public List<String> findDocumentsByEventname(String eventName) {
		return sentenceMapper.findDocumentsByEventname(eventName);
	}

	public List<TacSentence> findByDocName(String docName) {
		return sentenceMapper.findByDocName(docName);
	}

	public List<TacSentence> findAllSentences() {
		return sentenceMapper.findAllSentences();
	}

	public void update(TacSentence sentence) {
		sentenceMapper.update(sentence);
		commit();
	}

	public Matrix merge(Map<String, Double> energy,
			Matrix sentenceMatrix, CountMapper cm) {//把能量值与lsa之后的值合并

		for(Entry<String, Double> entry : energy.entrySet()){
			String word = entry.getKey();
			int rowNum = cm.getSeqNumber(word);
			double value = entry.getValue();
			MatrixUtils.addValueToRow(sentenceMatrix, value, rowNum);
		}
		return sentenceMatrix;
	}

	public Matrix plusEnergyLast(Matrix wordsWeightCurrent,
			Map<String, Double> energyLast, CountMapper cm) {
		decayEnergy(energyLast);//先衰减能量
		return merge(energyLast, wordsWeightCurrent, cm);
	}
	
	public void decayEnergy(Map<String, Double> energy){
		for(Entry<String, Double> entry : energy.entrySet()){
			double value = entry.getValue();
			if(value > 0){
				value = (value - beta) > 0 ? (value - beta) : 0 ;
			}
			energy.put(entry.getKey(), value);
		}
	}

	public void mergeEnergy(Map<String, Double> energyLast, Map<String, Double> energy) {
		for(Entry<String, Double> entry : energy.entrySet()){
			String word = entry.getKey();
			if(energyLast.containsKey(word)){
				energyLast.put(word, energyLast.get(word) + entry.getValue());
			}else{
				energyLast.put(word, entry.getValue());
			}
		}
	}

	public void computeAgingFeature(Matrix wordsWeightToday,
			List<TacSentence> sentences) {
		int cols = wordsWeightToday.getColumnDimension();
		if(cols != sentences.size()){
			logger.error("矩阵列数不等于句子数，肯定算错了");
			System.exit(0);
		}
		int i = 0;
		for(TacSentence sentence : sentences){
			double agingValue = MatrixUtils.computeColsSum(wordsWeightToday, i);
			sentence.setAging(agingValue);
			i++;
		}
	}

	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}
	public void setBeta(double beta) {
		this.beta = beta;
	}

	//各种表面特征
	public void computeSurfaceFeatures(List<TacSentence> sentences) {
		for(TacSentence sentence : sentences){
			sentence.setLength(sentence.getContent().length());
		}
		
	}

	public void computeNoveltyFeatures(List<TacSentence> sentences,
			List<TacSentence> summarySentencesLast) {
		for(TacSentence sentence : sentences){
			if(summarySentencesLast == null || summarySentencesLast.size() == 0){
				sentence.setNovelty(1);
			}else{
				double dis = 0;
				for(TacSentence summary : summarySentencesLast){
					dis += JaccardSim(sentence, summary);
				}
				double novelty = 1 - dis > 0 ? (1 - dis) : 0;//保证新鲜度>=0；当有句子完全重复的时候会出现负数.
//				double novelty = 1 - dis ;
				sentence.setNovelty(novelty);
//				logger.info("句子id:{}, 新鲜度：{}", sentence.getId(), novelty);
//				if(novelty <= 0){
//					logger.error("原句{}, 新鲜度:{} 新鲜度为负数，不科学！ ----------------------------------", sentence.getId(), novelty);
//					logger.error("原句:{}",sentence.getContent());
//					for(TacSentence summary : summarySentencesLast){
//						logger.error("摘要：{}",summary);
//					}
////					System.exit(0);
//				}
			}
		}
		
	}

	private double JaccardSim(TacSentence sentence, TacSentence summary) {
		Set<String> wordsCount = new HashSet<>(sentence.getTF().keySet());
		wordsCount.addAll(summary.getTF().keySet());
		int union = wordsCount.size();
		int jiao = sentence.getTF().size() + summary.getTF().size() - union ;
		double res = (double)jiao/union;
		return res;
	}

	public void computeImportanceFeatures(List<TacSentence> sentences,
			Map<String, Double> wordsIDF) {
		for(TacSentence sentence : sentences){
			double weight = 0;
			for(Entry<String, Integer> entry : sentence.getTF().entrySet()){
				String word = entry.getKey();
				int count = entry.getValue();
				double tmp = count * wordsIDF.get(word);
				weight += tmp;
			}
			sentence.setImportance(weight);
		}
		
	}

	
}
