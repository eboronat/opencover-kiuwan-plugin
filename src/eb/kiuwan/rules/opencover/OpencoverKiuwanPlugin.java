package eb.kiuwan.rules.opencover;
// MIT License
//
// Copyright (c) 2020 E.BORONAT, Kiuwan.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.als.core.AbstractRule;
import com.als.core.RuleContext;
import com.als.core.ast.BaseNode;

/**
 * @author eboronat
 * This rule load 'OpenCover' report and generates kiuwan violations for each failed condition.
 */
public class OpencoverKiuwanPlugin extends AbstractRule { 
	private final static Logger logger = Logger.getLogger(OpencoverKiuwanPlugin.class);

	private String COBERTURA_REPORT_NAME;
	private double thresholdMax;
	private double thresholdMin;

	public void initialize (RuleContext ctx) { 
		
		super.initialize(ctx);	
		thresholdMax = getProperty("maxThreshold",50); //Take threshold from rule definition. Default 50
		thresholdMin = getProperty("minThreshold",0); //Take threshold from rule definition. Default 0
		COBERTURA_REPORT_NAME = getProperty("COVERAGE_REPORT_NAME", "opencover.xml"); //Take report name. Default opencover.xml
		
		File baseDir = ctx.getBaseDirs().get(0);
		logger.debug("initialize: " +  this.getName() + " : " + baseDir);

	}


	protected void visit (BaseNode root, final RuleContext ctx) { 
		// this method is run once for all source files under analysis.
		// this method is left in blank intentionally.
	}


	public void postProcess (RuleContext ctx) { 
		// this method is run once for analysis
		super.postProcess(ctx); 
		logger.debug("postProcess: " +  this.getName());

		File baseDir = ctx.getBaseDirs().get(0);

		// iterates over 'cobertura' reports files.
		try {
			Files.walk(Paths.get(baseDir.getAbsolutePath()))
			.filter(Files::isRegularFile)
			.filter(p -> p.getFileName().toString().equals(COBERTURA_REPORT_NAME))
			.forEach(p -> {
				try {
					processCoberturaReportFile(ctx, p);
				} catch (ParserConfigurationException | SAXException | IOException e) {
					logger.error("Error parsing file " + p.getFileName() + ". ", e);
				}
			});
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	private void processCoberturaReportFile(RuleContext ctx, Path p) throws ParserConfigurationException, SAXException, IOException {
		logger.debug("processing: " +  p);

		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		factory.setFeature("http://xml.org/sax/features/validation", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);  
		factory.setFeature("http://xml.org/sax/features/resolve-dtd-uris", false);
		
		SAXParser parser = factory.newSAXParser();

		CoberturaReportHandler handler = new CoberturaReportHandler(ctx, p.toFile());
		parser.parse(p.toFile(), handler);
	}	

	/**
	 * The cobertura xml report handler
	 * @author eboronat
	 */
	class CoberturaReportHandler extends DefaultHandler {
		private RuleContext ctx;
		private File file;
		private Locator locator = null;

		private boolean inFullName = false;
		private String fileName = "";
		private boolean inClass = false;
		private double dCoverage = -1;

		public CoberturaReportHandler(RuleContext ctx, File file) {
			super();
			this.ctx = ctx;
			this.file = file;

			this.setDocumentLocator(locator);
		}

		public void setDocumentLocator(Locator locator) {
			this.locator = locator;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			if (qName.equalsIgnoreCase("class")) {
				inClass = true;
			}
			
			else if (inClass && qName.equalsIgnoreCase("summary")) {
				String coverage = attributes.getValue("sequenceCoverage");
				dCoverage = Double.parseDouble(coverage);
				logger.debug("CoberturaReportHandler.startElement(summary, Coverage=" + dCoverage + "%)");
			}
			
			else if (inClass && qName.equalsIgnoreCase("FullName")) {
				inFullName = true;
			} 
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if (inClass && qName.equalsIgnoreCase("FullName")) { //After FullName no more Summary tags taken
				inFullName = false;
				inClass = false;
				dCoverage = -1;
				logger.debug("CoberturaReportHandler.endElement(FullName)");
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			if (inFullName && dCoverage != -1) {
				fileName = new String(ch, start, length);
				
				logger.debug("CoberturaReportHandler.characters(class" + ", "+ fileName + ")");
				
				if (thresholdMax > dCoverage && thresholdMin <= dCoverage) { 
					try {
						Files.walk(Paths.get(ctx.getBaseDirs().get(0).getAbsolutePath()))
						.filter(Files::isRegularFile)
						//Compare file name w/o extensions is equal to file name in fileName var
						.filter(pAnalyzed -> pAnalyzed.getFileName().toString().indexOf(".") != -1
								&& pAnalyzed.getFileName().toString().substring(0, pAnalyzed.getFileName().toString().indexOf(".")).equals(fileName.substring(fileName.lastIndexOf(".")+1, fileName.length()))
								&& (pAnalyzed.getFileName().toString().endsWith(".cs")
								|| pAnalyzed.getFileName().toString().endsWith(".fs")
								|| pAnalyzed.getFileName().toString().endsWith(".vb")
								|| pAnalyzed.getFileName().toString().endsWith(".asp")
								|| pAnalyzed.getFileName().toString().endsWith(".aspx") ))
						.forEach(pAnalyzed -> {
							fileName = pAnalyzed.toAbsolutePath().toString();
							logger.debug("CoberturaReportHandler.startElement(Found file: " + fileName + ")");
							ctx.setSourceCodeFilename(new File(fileName));
							ctx.getReport().addRuleViolation(createRuleViolation(ctx, 1, "The coverage is: " + dCoverage + "%","Poor coverage"));
						});
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				dCoverage = -1;
			}
		}
	}
}
