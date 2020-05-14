package ghsmith.olfcsv2haml;

import ghsmith.olfcsv2haml.data.Haml;
import ghsmith.olfcsv2haml.data.ObjectFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class MainFileProcessor {

    public static void main(String args[]) throws IOException, JAXBException {

        ObjectFactory of = new ObjectFactory();

        Map<String, Haml> hamlMapBySampleId = new HashMap<>();

        for(String csvFileName : args) {
        
            CSVParser csvParser = CSVParser.parse(new File(csvFileName), Charset.defaultCharset(), CSVFormat.DEFAULT.withFirstRecordAsHeader());

            Haml.PatientAntibodyAssessment.SolidPhasePanel spp = null;
            String sampleId = null;

            for(CSVRecord csvRecord : csvParser) {

                if(!csvRecord.get("SampleIDName").equals(sampleId)) {

                    sampleId = csvRecord.get("SampleIDName");
                    System.out.println(sampleId);

                    Haml haml = hamlMapBySampleId.get(sampleId);

                    if(haml == null) {

                        haml = of.createHaml();
                        haml.setVersion("research-use-only-0-0-0");
                        hamlMapBySampleId.put(sampleId, haml);

                        Haml.PatientAntibodyAssessment paa = of.createHamlPatientAntibodyAssessment();
                        haml.getPatientAntibodyAssessment().add(paa);
                        paa.setSampleID(sampleId);
                        paa.setPatientID("not-available");
                        paa.setReportingCenterID("Emory University Hospital");
                        paa.setSampleTestDate(null);

                    }

                    spp = of.createHamlPatientAntibodyAssessmentSolidPhasePanel();
                    haml.getPatientAntibodyAssessment().get(0).getSolidPhasePanel().add(spp);
                    spp.setKitManufacturer("OneLambda");
                    spp.setLot(csvFileName);

                }

                if(csvRecord.get("NormalValue") == null || csvRecord.get("NormalValue").length() == 0) {
                    continue;
                }

                Haml.PatientAntibodyAssessment.SolidPhasePanel.Bead bead = of.createHamlPatientAntibodyAssessmentSolidPhasePanelBead();
                spp.getBead().add(bead);
                bead.setHLAAlleleSpecificity(csvRecord.get("Specificity").replaceAll("(-,?)|(-$)", "").replaceAll(",$", ""));
                bead.setRawMFI(Math.round(Float.valueOf(csvRecord.get("NormalValue"))));

            }
            
        }

        for(String sampleId : hamlMapBySampleId.keySet()) {

            for(
                Haml.PatientAntibodyAssessment.SolidPhasePanel spp
                : hamlMapBySampleId.get(sampleId).getPatientAntibodyAssessment().get(0).getSolidPhasePanel()
            ) {
                int ranking = 0;
                Haml.PatientAntibodyAssessment.SolidPhasePanel.Bead lastBead = null;
                for(
                    Haml.PatientAntibodyAssessment.SolidPhasePanel.Bead bead
                    : spp.getBead().stream()
                    .sorted(new Comparator<Haml.PatientAntibodyAssessment.SolidPhasePanel.Bead>() {
                       @Override
                       public int compare(Haml.PatientAntibodyAssessment.SolidPhasePanel.Bead o1, Haml.PatientAntibodyAssessment.SolidPhasePanel.Bead o2) {
                           return o1.getRawMFI().compareTo(o2.getRawMFI());
                       }
                    }).collect(Collectors.toList())
                ) {
                    bead.setRanking(bead.getRawMFI() > (lastBead != null ? lastBead.getRawMFI() : 0) ? ++ranking : ranking);
                    lastBead = bead;
                }
            }

            JAXBContext jc = JAXBContext.newInstance(new Class[] { Haml.class });
            Marshaller m = jc.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, new Boolean(true));
            m.marshal(hamlMapBySampleId.get(sampleId), new FileOutputStream(new File(sampleId.trim().replaceAll(" ", "_") + ".xml")));

        }

    }
    
}
