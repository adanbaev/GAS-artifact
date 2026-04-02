package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.FreqDao;
import kg.gov.nas.licensedb.dao.OwnerDao;
import kg.gov.nas.licensedb.dao.SiteDao;
import kg.gov.nas.licensedb.dto.FreqResult;
import kg.gov.nas.licensedb.dto.OwnerModel;
import kg.gov.nas.licensedb.dto.OwnerView;
import kg.gov.nas.licensedb.dto.SiteModel;
import kg.gov.nas.licensedb.enums.OwnerBasis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerService {
    private final OwnerDao ownerDao;
    private final SiteDao siteDao;

    private final FreqDao freqDao;
    public OwnerView getById(Long id){
        OwnerModel model = ownerDao.getById(id);
        List<SiteModel> sites = siteDao.getByOwnerId(id);
        OwnerView view = new OwnerView();
        view.setOwnerModel(model);
        view.setSites(sites);
        return view;
    }

    public OwnerModel getByIdFull(Long id){
        return ownerDao.getByIdFull(id);
    }

    public List<OwnerModel> getByIdsFull(List<Long> ids, String inn, OwnerBasis basis){
        List<OwnerModel> models = new ArrayList<>();
        for (Long id: ids){
            OwnerModel model = ownerDao.getByIdFull(id);
            model.setInn(inn);
            model.setBasis(basis);
            models.add(model);
        }

        return models;
    }

    @Transactional
    public List<FreqResult> copy(OwnerView ownerView){
        if(ownerView.getSiteId() == null){
            List<SiteModel> sites = siteDao.getByOwnerId(ownerView.getOwnerModel().getOwnerId());
            ownerView.setSites(sites);
        }

        return ownerDao.copy(ownerView);
    }
}
