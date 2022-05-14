package wooteco.subway.service;

import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wooteco.subway.dao.LineDao;
import wooteco.subway.dao.SectionDao;
import wooteco.subway.dao.SectionDaoV2;
import wooteco.subway.dao.StationDao;
import wooteco.subway.domain.Line;
import wooteco.subway.domain.Section;
import wooteco.subway.domain.SectionV2;
import wooteco.subway.domain.Sections;
import wooteco.subway.domain.Station;
import wooteco.subway.dto.LineRequest;
import wooteco.subway.dto.LineResponse;
import wooteco.subway.dto.StationResponse;

@Service
public class LineService {

    private static final int DELETE_FAIL = 0;

    private final StationDao stationDao;
    private final LineDao lineDao;
    private final SectionDao sectionDao;
    private final SectionDaoV2 sectionDaoV2;

    public LineService(StationDao stationDao, LineDao lineDao, SectionDao sectionDao,
                       SectionDaoV2 sectionDaoV2) {
        this.stationDao = stationDao;
        this.lineDao = lineDao;
        this.sectionDao = sectionDao;
        this.sectionDaoV2 = sectionDaoV2;
    }

    @Transactional
    public Long save(LineRequest request) {
        validateDuplicateName(request.getName());
        validateDuplicateSections(request);
        final Line line = new Line(request.getName(), request.getColor());
        final Long lineId = lineDao.save(line);

        final Station upStation = stationDao.findById(request.getUpStationId());
        final Station downStation = stationDao.findById(request.getDownStationId());

        final SectionV2 section = new SectionV2(lineId, upStation, downStation, request.getDistance());
        sectionDaoV2.save(section);

        return lineId;
    }

    private void validateDuplicateName(String name) {
        final boolean isExist = lineDao.findAll().stream()
                .anyMatch(line -> line.getName().equals(name));
        if (isExist) {
            throw new IllegalArgumentException("중복된 지하철 노선이 존재합니다.");
        }
    }

    private void validateDuplicateSections(LineRequest request) {
        if (request.getUpStationId().equals(request.getDownStationId())) {
            throw new IllegalArgumentException("상행과 하행 종점이 동일합니다.");
        }
    }

    public List<LineResponse> findAll() {
        return lineDao.findAll().stream()
                .map(line -> findById(line.getId()))
                .collect(toUnmodifiableList());
    }

    public LineResponse findById(Long id) {
        final Line line = lineDao.findById(id);

        final List<Section> sections = sectionDao.findAllByLineId(line.getId());
        final List<Station> stations = findStationBySections(sections);

        final List<StationResponse> stationsResponses = createStationResponseByStation(stations);

        return new LineResponse(line.getId(), line.getName(), line.getColor(), stationsResponses);
    }

    private List<Station> findStationBySections(List<Section> sections) {
        final List<Station> stations = new ArrayList<>();
        for (Section section : sections) {
            stations.add(findByStationId(section.getUpStationId()));
            stations.add(findByStationId(section.getDownStationId()));
        }
        return stations.stream()
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    private Station findByStationId(Long id) {
        return stationDao.findById(id);
    }

    private List<StationResponse> createStationResponseByStation(List<Station> stations) {
        return stations.stream()
                .map(station -> new StationResponse(station.getId(), station.getName()))
                .collect(Collectors.toList());
    }

    public Long updateByLine(Long id, LineRequest request) {
        final Line updateLine = new Line(id, request.getName(), request.getColor());

        return lineDao.updateByLine(updateLine);
    }

    public void deleteById(Long id) {
        final int isDeleted = lineDao.deleteById(id);

        if (isDeleted == DELETE_FAIL) {
            throw new IllegalArgumentException("존재하지 않는 노선입니다.");
        }
    }
}
