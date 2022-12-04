package jpabook.jpashop.api;

import jpabook.jpashop.domain.Member;
import jpabook.jpashop.service.MemberService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    /**
     * 등록 V1: 요청 값으로 Member 엔티티를 직접 받는다.
     * 문제점
     * - 엔티티에 프레젠테이션 계층을 위한 로직이 추가된다.
     *   - 엔티티에 API 검증을 위한 로직이 들어간다. (@NotEmpty 등등)
     *   - 실무에서는 회원 엔티티를 위한 API가 다양하게 만들어지는데, 한 엔티티에 각각의 API를 위한 모든 요청 요구사항을 담기는 어렵다.
     * - 엔티티가 변경되면 API 스펙이 변한다.
     * 결론
     * - API 요청 스펙에 맞추어 별도의 DTO를 파라미터로 받는다.
     */

    /**
     * 회원 등록 , json으로 온 바디를 Member로 매핑하여 넣어줌
     * json 데이터를 Member로 바꿔주는
     *
     * 엔티티인 Member에 검증로직이 있는건 올바르지 않음, 이름이 필수인 API도 있고 아닌 API도 있을 수 있기 때문에
     * 이름 필드의 이름을 변경하면 오류
     * 엔티티를 손대서 API스펙 자체가 변하는게 문제, 엔티티는 여러 곳에서 써서 바뀔 확률이 높음
     * 엔티티와 API는 1대1로 매핑되기 때문에
     * API스펙을 위한 별도의 데이터 트랜스 오브젝트를 만들어야함
     * 그래서 API를 만들때는 엔티티를 파라미터로 받으면 안됨, 외부에 노출해서도 안됨
     */
    @PostMapping("/api/v1/members")
    public CreateMemberResponse saveMemberV1(@RequestBody @Valid Member member) {
        Long id = memberService.join(member);

        return new CreateMemberResponse(id);
    }


    /**
     * 장점, API스펙이 안바뀜, Member 엔티티를 바뀌어도 여기서 컴파일 오류나서 쉽게 알아차림
     * Membber객체를 파라미터로 받아오면 Member의 필드가 정확하게 뭐가 넘어온지 모름
     * 근데 이렇게 CreateMemberRequest인 DTO로 받으면 뭘 받았는지 명확하게 인지
     * 우지보수가 좋음, 엔티티와 API스펙 명확하게 분리
     *
     * 엔티티 외부에 노출하거나 파라미터로 그대로 받는거 금지
     * 항상 API는 요청과 응답을 엔티티로 사용하지 않는다
     *
     * 엔티티와 프레젠테이션 로직을 분리 가능
     *
     * 요청이 오면 @RequestBody로 CreateMemberRequest에 바인딩됨됨
     * CreateMemberRequest는 별도의 트랜스 오브젝트
     */
    @PostMapping("/api/v2/members")
    public CreateMemberResponse saveMemberV2(@RequestBody @Valid CreateMemberRequest request) {

        //Member객체 새로 생성
        Member member = new Member();
        member.setName(request.getName());

        Long id = memberService.join(member);

        return new CreateMemberResponse(id);
    }

    /**
     * 수정 API
     */
    @PutMapping("/api/v2/members/{id}")
    public UpdateMemberResponse updateMemberV2(
            //경로에 넘어온 id를 가져옴
            @PathVariable("id") Long id,
            @RequestBody @Valid UpdateMemberRequest request) {

        //커맨드와 쿼리 분리
        memberService.update(id, request.getName());

        //트랜잭션이 다 끝나고 나서 id를 기지고 쿼리를 해서 가지고 온다음
        Member findMember = memberService.findOne(id);

        //응답 DTo객체 생성해 반환
        //@AllArgsConstructor를 썻기 때문에 파라미터를 다 넣어줘야함
       return new UpdateMemberResponse(findMember.getId(), findMember.getName());
    }

    /**
     * 조회 V1: 안 좋은 버전, 모든 엔티티가 노출, @JsonIgnore -> 이건 정말 최악, api가 이거 하나인가! 화면에 종속적이지 마라!
     * 문제점
     * 엔티티에 프레젠테이션 계층을 위한 로직이 추가된다.
     * 기본적으로 엔티티의 모든 값이 노출된다.
     * 응답 스펙을 맞추기 위해 로직이 추가된다. (@JsonIgnore, 별도의 뷰 로직 등등)
     * 실무에서는 같은 엔티티에 대해 API가 용도에 따라 다양하게 만들어지는데, 한 엔티티에 각각의
     * API를 위한 프레젠테이션 응답 로직을 담기는 어렵다.
     * 엔티티가 변경되면 API 스펙이 변한다.
     * 추가로 컬렉션을 직접 반환하면 항후 API 스펙을 변경하기 어렵다.(별도의 Result 클래스 생성으로 해결)

     * 결론
     * API 응답 스펙에 맞추어 별도의 DTO를 반환한다
     */
    @GetMapping("/api/v1/members")
    public List<Member> membersV1() {
        //@RestController이므로 리스트객체들이 json으로 바뀜
        return memberService.findMembers();
    }

    /**
     * 조회 V2: 응답 값으로 엔티티가 아닌 별도의 DTO를 반환한다.
     */
    //Result라는 별도의 DTO 클래스 생성
    @GetMapping("/api/v2/members")
    public Result membersV2() {

        List<Member> findMembers = memberService.findMembers();

        /**
         * Member클래스의 name필드 이름을 바꿔도 컴파일 오류남, 스펠이 변하진 않음
         * 엔티티 -> DTO 변환, 스트림을 해서 맵으로 돌림
         * Member엔티티에서 이름을 꺼내와 DTO로 넣음
         *  List<Member>를 List<MemberDto>로 바꿈
         */
        List<MemberDto> collect = findMembers.stream()
                .map(m -> new MemberDto(m.getName()))
                .collect(Collectors.toList());

        /**
         * 껍데기를 씌워 감싸줌, 안그럼 제이슨 배열 타입으로 나가므로 유연성이 떨어짐
         */
        return new Result(collect);
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private T data;
    }


    /**
     * 단순하게 이름만 넘기는, 노출 시킬것만 여기에 넣음
     * 외부 때문에 내부 꺼를 못바꾸는 상황이 생기지 않는다.
     */
    @Data
    @AllArgsConstructor
    static class MemberDto {
        private String name;
    }

    /**
     * 입데이트용 요청 DTO
     */
    @Data
    static class UpdateMemberRequest {
        private String name;
    }

    /**
     * 입데이트용 응답 DTO
     */
    @Data
    //모든 필드를 넣은 생성자 생성
    @AllArgsConstructor
    static class UpdateMemberResponse {
        private Long id;
        private String name;
    }


    /**
     * API스펙 자체가 name만 받게 되있구나
     */
    @Data
    static class CreateMemberRequest {

        //여기다 @Notempty같은 필요한 검증 로직 넣으면 됨
        private String name;
    }

    @Data
    static class CreateMemberResponse {
        private Long id;

        public CreateMemberResponse(Long id) {
            this.id = id;
        }
    }
}